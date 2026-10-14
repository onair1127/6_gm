package init;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import javax.json.Json;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.convertapi.client.Config;
import com.convertapi.client.ConvertApi;
import com.convertapi.client.Param;

import org.apache.commons.io.FilenameUtils;
import org.im4java.core.IMOperation;
import org.im4java.core.ImageCommand;
import org.im4java.process.ArrayListOutputConsumer;

public class ImageProcessor implements RequestHandler<Map<String, String>, String> {

     private static final int MAX_THUMBNAIL_WIDTH = 1000;
     private S3Utils s3Utils;
     private LambdaLogger logger;
     private String awsRequestId;

     @Override
     public String handleRequest(Map<String, String> input, Context context) {
          try {
               this.s3Utils = new S3Utils(context.getAwsRequestId(), context.getLogger());
               // this.s3Utils.awsRequestId = context.getAwsRequestId();
               // this.s3Utils.logger = context.getLogger();
               this.s3Utils.isBoard = input.get("mode").equals("board");

               this.awsRequestId = context.getAwsRequestId();
               this.logger = context.getLogger();

               logger.log("[" + awsRequestId + "] ((( Start processing imageProcessor )))\n");

               String filename = input.get("filename");
               String username = input.get("username");
               String ext = FilenameUtils.getExtension(filename);
               InputStream in = s3Utils.getObject(username, filename);

               if (input.get("mode").equals("board")) {
                    Files.createDirectories(Paths.get("/tmp", filename.split("/")[0]));
               }

               if (ext.toUpperCase().equals("HEIC")) {
                    in = convertHeicToJpg(in, username, filename, ext);
                    filename = filename.replaceAll(ext, "jpg");
                    ext = "jpg";
                    Files.copy(in, Paths.get("/tmp", filename), StandardCopyOption.REPLACE_EXISTING);
                    logger.log("[" + awsRequestId + "] File exists : " + Files.exists(Paths.get("/tmp", filename)) + "\n");
               } else {
                    Files.copy(in, Paths.get("/tmp", filename), StandardCopyOption.REPLACE_EXISTING);
                    logger.log("[" + awsRequestId + "] File exists : " + Files.exists(Paths.get("/tmp", filename)) + "\n");
               }

               if (input.get("mode").equals("profile")) {
                    String thumbnail = createThumbnail(username, filename, ext);
                    String profile = createProfile(username, filename, ext);
                    String result = Json.createObjectBuilder()
                         .add("thumbnail", thumbnail)
                         .add("profile", profile)
                         .build().toString();
                    
                    /**
                     * 아래 코드 한 줄은 요청의 원본파일을 삭제하는 코드이나
                     * 혹시 모를 오류 또는 다양하게 활용할 수 있다는 것을 대비하여 원본파일을 삭제 안하도록 결정함
                     */
                    // s3Utils.deleteObject(username, filename);
                    return result;

               } else if (input.get("mode").equals("board")) {
                    String thumbnail = createThumbnail(username, filename, ext);
                    String result = Json.createObjectBuilder()
                         .add("thumbnail", thumbnail)
                         .add("card", "")
                         .build().toString();
                    
                    /**
                     * 얘는 왜 원본파일 삭제 안함? : 운영자 소식이라 별 문제 없을듯?
                     */
                    s3Utils.deleteObject(username, filename);
                    return result;
               } else if (input.get("mode").equals("userProfile")) {
                    String thumbnail = createThumbnail(username, filename, ext);
                    String logo = createLogo(username, filename, ext);
                    String result = Json.createObjectBuilder()
                         .add("thumbnail", thumbnail)
                         .add("logo", logo)
                         .build().toString();
                    
                    s3Utils.deleteObject(username, filename);
                    return result;
               
               } else {
                    String thumbnail = createThumbnail(username, filename, ext);
                    String card = createCard(username, filename, ext);
                    String result = Json.createObjectBuilder()
                         .add("thumbnail", thumbnail)
                         .add("card", card)
                         .build().toString();
               
                    /**
                     * 아래 코드 한 줄은 요청의 원본파일을 삭제하는 코드이나
                     * 혹시 모를 오류 또는 다양하게 활용할 수 있다는 것을 대비하여 원본파일을 삭제 안하도록 결정함
                     */
                    // s3Utils.deleteObject(username, filename);
                    return result;
                    
               }

          } catch (Exception e) {
               e.printStackTrace();
               return null;
          }
     }

     public String createThumbnail(String username, String filename, String ext) {

          try {
               String thumbnail = filename.replace(ext, "thumb." + ext);
               logger.log("[" + awsRequestId + "] *** Start to create thumbnail *** \n");

               Size size = getSize(username, filename);
               int MAX_SIZE = size.getWidth() <= MAX_THUMBNAIL_WIDTH ? size.getWidth() : MAX_THUMBNAIL_WIDTH;
               logger.log("[" + awsRequestId + "] 섬네일에서 가로 : " + size.getWidth() + ", 세로 : " + size.getHeight() + "\n");

               ImageCommand cmd = new ImageCommand();
               cmd.setCommand("gm", "convert");
               cmd.setSearchPath("./opt/bin");
               
               IMOperation op = new IMOperation();
               op.addImage("/tmp/" + filename);
               op.size(MAX_SIZE, MAX_SIZE);
               op.resize(MAX_SIZE, MAX_SIZE);
               op.quality(90d);
               op.gravity("center");
               op.dither();
               op.addImage("/tmp/" + thumbnail);
               cmd.run(op);

               logger.log("[" + awsRequestId + "] *** Thumbnail created ***\n");
               return s3Utils.upload(username, Paths.get("/tmp", thumbnail));

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] !!! Failed to create thumbnail image [" + username + " : " + filename + "] !!!\n");
               e.printStackTrace();
               return null;
          }
     }

     public String createCard(String username, String filename, String ext) {

          try {
               String card = filename.replace(ext, "card." + ext);
               filename = filename.replace(ext, "thumb." + ext);
               logger.log("[" + awsRequestId + "] *** Start to create card ***\n");

               Size size = getSize(username, filename);
               int MAX_SIZE = size.getWidth() <= size.getHeight() ? size.getWidth() : size.getHeight();
               logger.log("[" + awsRequestId + "] 카드에서 가로 : " + size.getWidth() + ", 세로 : " + size.getHeight() + ", 최종 사이즈 : " + MAX_SIZE + "\n");

               ImageCommand cmd = new ImageCommand();
               cmd.setCommand("gm", "convert");
               cmd.setSearchPath("./opt/bin");

               IMOperation op = new IMOperation();
               op.addImage("/tmp/" + filename);
               op.gravity("center");
               op.extent(MAX_SIZE, MAX_SIZE);
               op.addImage("/tmp/" + card);
               cmd.run(op);

               logger.log("[" + awsRequestId + "] *** Card created ***\n");

               return s3Utils.upload(username, Paths.get("/tmp", card));

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] !!! Failed to create card image [" + username + " : " + filename + "] !!!\n");
               e.printStackTrace();
               return null;
          }
     }

     public String createProfile(String username, String filename, String ext) {

          try {
               String profile = filename.replace(ext, "profile." + ext);
               filename = filename.replace(ext, "thumb." + ext);
               logger.log("[" + awsRequestId + "] *** Start creating profile image ***\n");

               ImageCommand cmd = new ImageCommand();
               cmd.setCommand("gm", "convert");
               cmd.setSearchPath("./opt/bin");

               IMOperation op = new IMOperation();
               op.addImage("/tmp/" + filename);
               op.gravity("center");
               op.blur(15.0, 8.0);
               op.quality(90d);
               op.addImage("/tmp/" + profile);
               cmd.run(op);

               logger.log("[" + awsRequestId + "] *** Profile created ***\n");
               return s3Utils.upload(username, Paths.get("/tmp", profile));

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] !!! Failed to create profile image [" + username + " : " + filename + "] !!!\n");
               e.printStackTrace();
               return null;
          }
     }

     public String createLogo(String username, String filename, String ext) {

          try {
               String logo = filename.replace(ext, "logo." + ext);
               filename = filename.replace(ext, "thumb." + ext);
               logger.log("[" + awsRequestId + "] *** Start creating logo image ***\n");

               ImageCommand cmd = new ImageCommand();
               cmd.setCommand("gm", "convert");
               cmd.setSearchPath("./opt/bin");
               
               IMOperation op = new IMOperation();
               op.addImage("/tmp/" + filename);
               op.quality(90d);
               op.gravity("center");
               op.resize(200, 200);
               op.dither();
               op.addImage("/tmp/" + logo);
               cmd.run(op);

               logger.log("[" + awsRequestId + "] *** Logo created ***\n");
               return s3Utils.upload(username, Paths.get("/tmp", logo));

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] !!! Failed to create Logo image [" + username + " : " + filename + "] !!!\n");
               e.printStackTrace();
               return null;
          }
     }

     public InputStream convertHeicToJpg(InputStream stream, String username, String filename, String ext) {
          try {
               Path tempFile = Paths.get("/tmp", filename);

               try {
                    Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);

               } catch (Exception e) {
                    logger.log("[" + awsRequestId + "] HEIC 임시 파일 저장에 실패하였습니다..\n");
                    e.printStackTrace();
                    return null;
               }

               Config.setDefaultSecret("secret_Svs5EKn8ucAqhQ0V");
               ConvertApi.convert("heic", "jpg",
                    new Param("File", tempFile)          
               ).get().saveFilesSync(Paths.get("/tmp"));

               String newFilename = filename.replaceAll(ext, "jpg");

               /*
                * 얘는 왜 원본파일 삭제함? : 필요 없다. HEIC 같은건.
                */
               s3Utils.deleteObject(username, filename);
               s3Utils.upload(username, Paths.get("/tmp", newFilename));
               return s3Utils.getObject(username, newFilename);
          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] HEIC 변환에 실패하였습니다.\n");
               e.printStackTrace();
               return null;
          }
     }
     
     public Size getSize(String username, String filename) {
          try {
               logger.log("[" + awsRequestId + "] *** Get image size ***\n");
               ImageCommand cmd = new ImageCommand();
               cmd.setCommand("gm", "identify");
               cmd.setSearchPath("./opt/bin");

               ArrayListOutputConsumer consumer = new ArrayListOutputConsumer();
               cmd.setOutputConsumer(consumer);

               IMOperation op = new IMOperation();
               op.format("%w,%h");
               op.addImage("/tmp/" + filename);
               cmd.run(op);

               String output = consumer.getOutput().get(0);
               String[] dimensions = output.split(",");

               return new Size(Integer.parseInt(dimensions[0]), Integer.parseInt(dimensions[1]));

          } catch (Exception e) {
               logger.log("[" + awsRequestId + "] !!! Failed to get image size [" + username + " : " + filename + "] !!!\n");
               e.printStackTrace();
               return null;
          }
     }
}
