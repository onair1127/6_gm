package init;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class S3Utils {

     protected LambdaLogger logger;
     protected String awsRequestId;
     protected boolean isBoard;

     private static String accessKey;
     private static String secretKey;

     private final String s3Url = "anseongapp-images.s3.ap-northeast-2.amazonaws.com";
     private final String cdnUrl = "image.anseongapp.com";

     private AmazonS3 s3;

     public S3Utils(String awsRequestId, LambdaLogger logger) {
          this.awsRequestId = awsRequestId;
          this.logger = logger;

          try {
               Properties properties = new Properties();
               properties.load(new FileInputStream("key.properties"));
               accessKey = new String(Base64.getDecoder().decode(properties.getProperty("api.aws.accessKey")));
               secretKey = new String(Base64.getDecoder().decode(properties.getProperty("api.aws.secretKey")));
               
               final AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
               s3 = AmazonS3ClientBuilder
                         .standard()
                         .withCredentials(new AWSStaticCredentialsProvider(credentials))
                         .withRegion(Regions.AP_NORTHEAST_2)
                         .build();
          } catch (Exception e) {
               e.printStackTrace();
               accessKey = "";
               secretKey = "";
               s3 = null;
          }
     }

     /**
      * 로컬에 저장한 파일의 Path로 업로드
      * 주로 비디오 컨버팅 후 마지막에 호출됨 
      *
      * @param username
      * @param filePath
      * @return
      */
     public String upload(String username, Path filePath) {
          try {
               File file = filePath.toFile();
               String filename = file.getName();
               String url;

               if (isBoard) {
                    filename = filePath.toString().replace("/tmp/", "");
               }

               logger.log("[" + awsRequestId + "] 저장할 파일 이름 : " + filename + "\n");

               if (!s3.doesBucketExistV2("anseongapp-images")) {
                    s3.createBucket(new CreateBucketRequest("anseongapp-images"))
                              .setCreationDate(new Date());
               }
               
               s3.putObject(
                    new PutObjectRequest("anseongapp-images", username + "/" + filename, file)
                         // .withCannedAcl(CannedAccessControlList.PublicRead)
                         );
                         
               url = s3.getUrl("anseongapp-images", username + "/" + filename).toString().replace(s3Url, cdnUrl);
               file = null;

               logger.log("[" + awsRequestId + "] Upload success to S3! : " + url + "\n");
               return url;

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] Failed to upload file to S3 : " + filePath.toString() + "\n");
               e.printStackTrace();
               return null;
          }
     }
     
     /**
      * InputStream으로 업로드
      * 오로지 CloudConvert 작업 후 결과물 반영을 위한 메서드.
      *
      * @param username
      * @param inputStream
      * @return
      */
     public String upload(String username, String filename, InputStream inputStream) {
          try {
               String url;

               logger.log("[" + awsRequestId + "] Cloud저장할 파일 이름 : " + filename + "\n");

               if (!s3.doesBucketExistV2("anseongapp-images")) {
                    s3.createBucket(new CreateBucketRequest("anseongapp-images"))
                              .setCreationDate(new Date());
               }
               
               s3.putObject(
                    new PutObjectRequest("anseongapp-images", "username", inputStream, null)
                         // .withCannedAcl(CannedAccessControlList.PublicRead)
                         );
                         
               url = s3.getUrl("anseongapp-images", username + "/" + filename).toString().replace(s3Url, cdnUrl);

               logger.log("[" + awsRequestId + "] Upload success to S3! : " + url + "\n");
               return url;

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] CloudConvert 이후 S3로 저장 실패 : " + username + "\n");
               e.printStackTrace();
               return null;
          }
     }
     
     public InputStream getObject(String username, String filename) {
          return s3
               .getObject("anseongapp-images", username + "/" + filename)
               .getObjectContent().getDelegateStream();
     }
     
     public void deleteObject(String username, String filename) {
          s3.deleteObject("anseongapp-images", username + "/" + filename);
     }
}
