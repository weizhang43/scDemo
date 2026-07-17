package com.example.scproduct.es;

import com.curry.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 商品描述 ES 索引读写服务。
 * 仅负责 proDesc 模糊检索召回 pId 列表，分页与排序仍在 MySQL 侧完成。
 * ES 不可用时调用方应捕获异常并降级到 MySQL LIKE。
 */
@Slf4j
@Service
public class ProductDescEsService {

    @Autowired
    private ElasticsearchRestTemplate esTemplate;

    @Value("${product.es.max-hit-ids:1000}")
    private int maxHitIds;

    /**
     * 启动时确保索引存在：@Document(createIndex=true) 只在 Repository 路径触发，
     * 直接用 ElasticsearchRestTemplate.search()/bulkIndex() 不会建表，
     * 所以这里显式建一次，避免首查 index_not_found。
     */
    @PostConstruct
    public void ensureIndex() {
        try {
            IndexOperations ops = esTemplate.indexOps(ProductDescDoc.class);
            if (!ops.exists()) {
                ops.create();
                ops.putMapping();
                log.info("[es:ensureIndex] index created, mapping applied");
            } else {
                // 索引已存在：重放 mapping，让 @Field 变更（如新增 @Field 在 @Id 上）能生效
                try {
                    ops.putMapping();
                    log.info("[es:ensureIndex] mapping re-applied on existing index");
                } catch (Exception me) {
                    log.warn("[es:ensureIndex] putMapping on existing index failed: {}", me.getMessage());
                }
            }
        } catch (Exception e) {
            // ES 未就绪不应阻塞应用启动 —— 查询时再走空结果降级
            log.warn("[es:ensureIndex] create index skipped, will retry lazily: {}", e.getMessage());
        }
    }

    /**
     * 按商品描述模糊检索，返回命中的 pId 列表。
     * 使用 match 查询，由 ik_smart 分词后逐 token 召回，效果优于 SQL LIKE。
     */
    public List<Integer> searchPIdsByDesc(String proDesc) {
        if (proDesc == null || proDesc.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.matchQuery("proDesc", proDesc.trim()))
                    .withPageable(PageRequest.of(0, maxHitIds));
            SearchHits<ProductDescDoc> hits = esTemplate.search(builder.build(), ProductDescDoc.class);
            List<Integer> ids = new ArrayList<>();
            int nullPidCount = 0;
            for (SearchHit<ProductDescDoc> hit : hits) {
                ProductDescDoc doc = hit.getContent();
                if (doc == null) continue;
                if (doc.getPId() == null) {
                    nullPidCount++;
                    continue;
                }
                ids.add(doc.getPId().intValue());
            }
            log.info("[es:searchPIdsByDesc] keyword='{}' totalHits={} returned={} nullPidSkipped={}",
                    proDesc, hits.getTotalHits(), ids.size(), nullPidCount);
            return ids;
        } catch (UncategorizedElasticsearchException | IndexNotFoundException e) {
            // 索引尚未创建或 ES 不可用：返回空让上层降级，而不是把 404 冒泡成接口 500
            log.warn("[es:searchPIdsByDesc] index unavailable, keyword='{}', type={}",
                    proDesc, e.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[es:searchPIdsByDesc] query failed, keyword='{}'", proDesc, e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量 upsert 商品到 ES（增量 Job 调用）。
     * bulk 失败时降级为逐条 index，定位并跳过坏数据，避免整批失败。
     */
    public int upsertBatch(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return 0;
        }
        List<IndexQuery> queries = new ArrayList<>(products.size());
        for (Product p : products) {
            ProductDescDoc doc = toDoc(p);
            if (doc.getPId() == null) {
                log.warn("[es:upsertBatch] skip null pId, productName={}", p.getPName());
                continue;
            }
            queries.add(new IndexQueryBuilder()
                    .withId(String.valueOf(doc.getPId()))
                    .withObject(doc)
                    .build());
        }
        if (queries.isEmpty()) {
            return 0;
        }
        int success = 0;
        try {
            esTemplate.bulkIndex(queries, ProductDescDoc.class);
            success = queries.size();
            log.info("[es:upsertBatch] bulk indexed {} docs", success);
        } catch (Exception bulkErr) {
            log.warn("[es:upsertBatch] bulk failed ({}), fallback to one-by-one", bulkErr.getMessage());
            for (IndexQuery q : queries) {
                try {
                    esTemplate.index(q, IndexCoordinates.of("product_desc"));
                    success++;
                } catch (Exception oneErr) {
                    log.error("[es:upsertBatch] single index fail, id={}", q.getId(), oneErr);
                }
            }
        }
        return success;
    }

    private ProductDescDoc toDoc(Product p) {
        ProductDescDoc doc = new ProductDescDoc();
        doc.setPId(p.getPId() == null ? null : p.getPId().longValue());
        doc.setPName(p.getPName());
        doc.setProDesc(p.getProDesc());
        doc.setIsExpired(p.getIsExpired());
        doc.setStock(p.getStock());
        doc.setUpdateTime(new Date());
        return doc;
    }
}
