package team.hotelchain.webcontent;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebsiteMediaVariantService {
    private static final List<Integer> TARGET_WIDTHS = List.of(640, 1280);

    private final JdbcTemplate jdbc;

    public WebsiteMediaVariantService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void enqueueEligible(UUID assetId, int sourceWidth) {
        for (int targetWidth : TARGET_WIDTHS) {
            if (targetWidth <= sourceWidth) {
                jdbc.update("""
                        insert into website_media_variant (id, asset_id, format, target_width, status)
                        values (?, ?, 'WEBP', ?, 'PENDING')
                        on conflict (asset_id, format, target_width) do nothing
                        """, UUID.randomUUID(), assetId, targetWidth);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<WebsiteMediaVariant>> findByAssetIds(List<UUID> assetIds) {
        if (assetIds.isEmpty()) return Map.of();
        String placeholders = String.join(", ", Collections.nCopies(assetIds.size(), "?"));
        Map<UUID, List<WebsiteMediaVariant>> variantsByAsset = new LinkedHashMap<>();
        jdbc.query("""
                select id, asset_id, format, target_width, status, storage_key, mime_type,
                       byte_size, width, height, attempt_count, last_error, updated_at
                  from website_media_variant
                 where asset_id in (%s)
                 order by target_width
                """.formatted(placeholders), rs -> {
            UUID assetId = rs.getObject("asset_id", UUID.class);
            String status = rs.getString("status");
            int targetWidth = rs.getInt("target_width");
            String deliveryUrl = "READY".equals(status)
                    ? "/api/website/media/" + assetId + "/variants/" + targetWidth + ".webp"
                    : null;
            WebsiteMediaVariant variant = new WebsiteMediaVariant(
                    rs.getObject("id", UUID.class), rs.getString("format"), targetWidth, status, deliveryUrl,
                    rs.getString("mime_type"), rs.getObject("byte_size", Long.class),
                    rs.getObject("width", Integer.class), rs.getObject("height", Integer.class),
                    rs.getInt("attempt_count"), rs.getString("last_error"),
                    rs.getObject("updated_at", OffsetDateTime.class));
            variantsByAsset.computeIfAbsent(assetId, ignored -> new ArrayList<>()).add(variant);
        }, assetIds.toArray());
        return variantsByAsset;
    }
}
