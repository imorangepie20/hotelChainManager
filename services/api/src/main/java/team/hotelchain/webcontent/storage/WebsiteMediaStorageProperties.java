package team.hotelchain.webcontent.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("website.media.storage")
public class WebsiteMediaStorageProperties {
    private WebsiteMediaStorageMode mode = WebsiteMediaStorageMode.LOCAL;
    private final S3 s3 = new S3();

    public WebsiteMediaStorageMode getMode() {
        return mode;
    }

    public void setMode(WebsiteMediaStorageMode mode) {
        this.mode = mode;
    }

    public S3 getS3() {
        return s3;
    }

    public static class S3 {
        private String endpoint;
        private String region;
        private String bucket;
        private boolean pathStyle;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }
    }
}
