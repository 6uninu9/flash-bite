package com.smart.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String region;
    private String bucketName;

    //通过环境变量来获取ID和Key
    private static final EnvironmentVariableCredentialsProvider credentialsProvider;

    static {
        try {
            credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        } catch (com.aliyuncs.exceptions.ClientException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建OSSClient实例
     *
     * @return OSSClient
     */
    private OSS getOssClient() {
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .build();
    }

    /**
     * 改造文件名，避免文件覆盖
     *
     * @return String
     */
    public static String getFilename(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename(); //获取最初的文件名，如1.png
        if (originalFilename != null) {
            //加上生成的随机数和文件扩展名改造文件名
            return UUID.randomUUID().toString() + "." + originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return null;
    }


    /**
     * 向OSS上传文件
     *
     * @param file, expirationMillis
     * @throws Exception 抛出异常
     */
    public String upload(MultipartFile file, long expirationMillis) throws Exception {
//        String bucketName = getBucketName();

        OSS ossClient = null;
        String fileName = null;
        try {
            // 2. 生成唯一文件名
            fileName = getFilename(file);
            // 3. 获取OSS Client
            ossClient = getOssClient();
            // 4. 获取文件输入流
            InputStream inputStream = file.getInputStream();
            // 5. 构建上传请求
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fileName, inputStream);
            // 6. 执行上传
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("文件上传成功，ETag：{}", result.getETag());
            // 7. 生成永久URL
            return getFileUrl(fileName, ossClient, expirationMillis);
        } catch (OSSException oe) {
            System.out.println("Caught an OSSException, which means your request made it to OSS, "
                    + "but was rejected with an error response for some reason.");
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
            throw new RuntimeException(oe);
        } catch (ClientException ce) {
            System.out.println("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message:" + ce.getMessage());
            throw new RuntimeException(ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 获取文件临时访问路径
     *
     * @param fileName
     * @param ossClient
     * @param expirationMillis
     * @return
     */
    public String getFileUrl(String fileName, OSS ossClient, long expirationMillis) {
        try {
            // 设置 URL 的过期时间
            Date expiration = new Date(System.currentTimeMillis() + expirationMillis);
            // 生成预签名 URL
            URL url = ossClient.generatePresignedUrl(bucketName, fileName, expiration);
            log.info("文件访问路径：{}", url);
            return url.toString();
        } catch (Exception e) {
            System.out.println("生成文件 URL 出现异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 重新获取url
     *
     * @param file
     * @param expirationMillis
     * @return
     * @throws IOException
     */
    public String getPresignedUrl(MultipartFile file, long expirationMillis) throws IOException {
        OSS ossClient = getOssClient();
        return getFileUrl(getFilename(file), ossClient, expirationMillis);
    }
}
