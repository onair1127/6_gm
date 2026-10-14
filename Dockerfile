FROM public.ecr.aws/lambda/java:17

COPY build/classes/java/main* ${LAMBDA_TASK_ROOT}
COPY build/resources/main* ${LAMBDA_TASK_ROOT}
COPY build/dependency/* ${LAMBDA_TASK_ROOT}/lib/
COPY graphicsmagick ./graphicsmagick/
# COPY gm.zip ${LAMBDA_TASK_ROOT}/opt/

RUN yum -y update && yum -y install gzip && yum -y install make \
     yum -y install \
          gcc-c++ \
          libtiff-devel \
          libpng-devel \
          libjpeg-devel \
          wget

RUN chmod -R 777 ./
RUN cd graphicsmagick && ./configure --prefix=/var/task/opt --enable-shared=no --enable-static=yes && make && make install
RUN chmod -R 777 ${LAMBDA_TASK_ROOT}/opt

RUN test -f ${LAMBDA_TASK_ROOT}/opt/bin/gm && echo ' *** It Exists ***'

# RUN unzip ${LAMBDA_TASK_ROOT}/opt/gm.zip -d ${LAMBDA_TASK_ROOT}/opt
# RUN test -f ${LAMBDA_TASK_ROOT}/opt/bin/gm && echo ' *** It Exists ***'

CMD [ "init.ImageProcessor::handleRequest" ]
