package com.magambell.server.common.s3;

import com.magambell.server.common.annotation.Adapter;
import com.magambell.server.common.s3.dto.ImageRegister;
import com.magambell.server.common.s3.dto.TransformedImageDTO;
import com.magambell.server.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@RequiredArgsConstructor
@Adapter
public class S3Adapter implements S3InputPort {

    private static final String SSL = "https://";
    private static final int MAX_EXTENSION_LENGTH = 10;
    private final S3Client s3Client;

    @Value("${spring.aws.cf}")
    private String AWS_CF_DISTRIBUTION;

    @Override
    public List<TransformedImageDTO> saveImages(final String imagePrefix,
                                                final List<ImageRegister> imageRegisters, final User user) {
        return imageRegisters.stream()
                .map(image -> {
                    String getImagePrefix = getImagePrefix(imagePrefix, image, user);

                    return new TransformedImageDTO(
                            image.id(),
                            getCloudFrontSignedUrl(getImagePrefix),
                            s3Client.getPreSignedUrl(getImagePrefix)
                    );
                })
                .toList();
    }

    @Override
    public List<TransformedImageDTO> saveImages(final String imagePrefix,
                                                final List<ImageRegister> imageRegisters, final Long id) {
        return imageRegisters.stream()
                .map(image -> {
                    String getImagePrefix = getImagePrefix(imagePrefix, image, id);

                    return new TransformedImageDTO(
                            image.id(),
                            getCloudFrontSignedUrl(getImagePrefix),
                            s3Client.getPreSignedUrl(getImagePrefix)
                    );
                })
                .toList();
    }

    @Override
    public TransformedImageDTO saveImage(final String imagePrefix,
                                         final ImageRegister imageRegister,
                                         final Long id) {

        String getImagePrefix = getImagePrefix(imagePrefix, imageRegister, id);

        return new TransformedImageDTO(
                imageRegister.id(),
                getCloudFrontSignedUrl(getImagePrefix),
                s3Client.getPreSignedUrl(getImagePrefix)
        );
    }

    @Override
    public void deleteS3Objects(final String imagePrefix, final User user) {
        s3Client.listObjectKeys(
                        imagePrefix + "/" + user.getUserRole() + "/" + user.getId()
                )
                .forEach(s3Client::deleteObjectS3);
    }

    @Override
    public void deleteS3Objects(final String imagePrefix, Long id) {
        s3Client.listObjectKeys(
                        imagePrefix + "/" + id
                )
                .forEach(s3Client::deleteObjectS3);
    }

    @Override
    public String getImagePrefix(final String imagePrefix, final ImageRegister imageRegisters,
                                 final User user) {
        String normalizedKey = normalizeImageKey(imageRegisters.key());
        return imagePrefix + "/" + user.getUserRole() + "/" + user.getId() + "/" + imageRegisters.id() + "_"
            + normalizedKey;
    }

    @Override
    public String getImagePrefix(final String imagePrefix, final ImageRegister imageRegisters, final Long id) {
        String normalizedKey = normalizeImageKey(imageRegisters.key());
        return imagePrefix + "/" + id + "/" + imageRegisters.id() + "_"
            + normalizedKey;
    }

    @Override
    public String getReadUrl(final String imagePathOrKey) {
        if (imagePathOrKey == null || imagePathOrKey.isBlank()) {
            return imagePathOrKey;
        }

        try {
            String objectKey = extractObjectKey(imagePathOrKey);
            return s3Client.getPreSignedGetUrl(objectKey);
        } catch (RuntimeException ignored) {
            // Do not fail the whole API when URL re-signing fails.
            return imagePathOrKey;
        }
    }

    private String getCloudFrontSignedUrl(final String imageKey) {
        return SSL + AWS_CF_DISTRIBUTION + "/" + imageKey;
    }

    private String extractObjectKey(final String imagePathOrKey) {
        if (imagePathOrKey.startsWith("http://") || imagePathOrKey.startsWith("https://")) {
            try {
                URI uri = URI.create(imagePathOrKey);
                String path = uri.getPath();
                if (path != null && !path.isBlank()) {
                    return path.startsWith("/") ? path.substring(1) : path;
                }
            } catch (IllegalArgumentException ignored) {
                return stripQueryAndFragment(imagePathOrKey);
            }
        }
        return stripQueryAndFragment(imagePathOrKey);
    }

    private String normalizeImageKey(final String imagePathOrKey) {
        if (imagePathOrKey == null || imagePathOrKey.isBlank()) {
            return "image";
        }

        String objectKey = extractObjectKey(imagePathOrKey);
        String fileName = extractFileName(objectKey);
        String extension = extractSafeExtension(fileName);
        return sha256Hex(objectKey) + extension;
    }

    private String extractFileName(final String objectKey) {
        int lastSlashIndex = objectKey.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < objectKey.length() - 1) {
            return objectKey.substring(lastSlashIndex + 1);
        }
        return objectKey;
    }

    private String extractSafeExtension(final String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }

        String extension = fileName.substring(dotIndex);
        if (extension.length() > MAX_EXTENSION_LENGTH) {
            return "";
        }
        return extension.matches("\\.[A-Za-z0-9]+") ? extension : "";
    }

    private String sha256Hex(final String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String stripQueryAndFragment(final String value) {
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');

        int cutIndex = -1;
        if (queryIndex >= 0 && fragmentIndex >= 0) {
            cutIndex = Math.min(queryIndex, fragmentIndex);
        } else if (queryIndex >= 0) {
            cutIndex = queryIndex;
        } else if (fragmentIndex >= 0) {
            cutIndex = fragmentIndex;
        }

        if (cutIndex >= 0) {
            return value.substring(0, cutIndex);
        }
        return value;
    }
}
