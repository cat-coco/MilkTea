package com.milktea.agent.rag;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class RagManager {

    private final Map<String, KnowledgeEntry> knowledgeBase = new ConcurrentHashMap<>();

    public RagManager() {
        initDefaultKnowledge();
    }

    private void initDefaultKnowledge() {
        addKnowledge("menu_classic", "经典珍珠奶茶", List.of("珍珠奶茶", "奶茶", "经典", "招牌"),
                "经典珍珠奶茶是本店招牌饮品，选用优质斯里兰卡红茶搭配新西兰进口奶源，珍珠Q弹有嚼劲。小杯12元/中杯15元/大杯18元。推荐半糖少冰。");

        addKnowledge("menu_matcha", "抹茶拿铁", List.of("抹茶", "拿铁", "绿茶"),
                "抹茶拿铁采用日本宇治抹茶粉，搭配鲜牛奶打制而成，茶香浓郁不苦涩。小杯14元/中杯17元/大杯20元。推荐少糖去冰。");

        addKnowledge("menu_mango", "杨枝甘露", List.of("杨枝甘露", "芒果", "甜品"),
                "杨枝甘露选用菲律宾吕宋芒搭配西柚果肉和椰浆，口感丰富层次分明。小杯16元/中杯19元/大杯22元。推荐正常糖少冰。");

        addKnowledge("menu_taro", "芋泥波波奶茶", List.of("芋泥", "波波", "芋头"),
                "芋泥波波奶茶使用新鲜槟榔芋手工研磨成泥，配上黑糖波波，绵密顺滑。小杯15元/中杯18元/大杯21元。推荐半糖正常冰。");

        addKnowledge("menu_strawberry", "草莓摇摇乐", List.of("草莓", "水果茶"),
                "草莓摇摇乐采用当季新鲜草莓搭配茉莉绿茶底，酸甜可口。小杯13元/中杯16元/大杯19元。推荐少糖少冰。");

        addKnowledge("menu_osmanthus", "桂花乌龙茶", List.of("桂花", "乌龙", "纯茶"),
                "桂花乌龙茶选用安溪铁观音搭配桂林金桂，清香怡人，适合不想喝奶茶的客户。小杯10元/中杯13元/大杯16元。推荐无糖去冰。");

        addKnowledge("menu_brown_sugar", "黑糖鹿丸鲜奶", List.of("黑糖", "鹿丸", "鲜奶"),
                "黑糖鹿丸鲜奶使用冲绳黑糖熬制糖浆，搭配手工鹿丸和鲜牛奶。小杯14元/中杯17元/大杯20元。推荐正常糖少冰。");

        addKnowledge("menu_grape", "多肉葡萄", List.of("葡萄", "多肉", "水果茶"),
                "多肉葡萄选用巨峰葡萄手工剥皮去籽，搭配四季春茶底，清爽解腻。小杯15元/中杯18元/大杯21元。推荐半糖正常冰。");

        addKnowledge("faq_delivery", "配送说明", List.of("配送", "外卖", "送货", "多久"),
                "目前仅支持到店自取，暂不提供外卖配送服务。下单后一般15-20分钟可以制作完成。");

        addKnowledge("faq_refund", "退单政策", List.of("退单", "退款", "取消", "退钱"),
                "制作前可免费取消订单；制作中取消需等制作完成后退单；已完成的订单在30分钟内可申请退单退款。");

        addKnowledge("faq_membership", "会员制度", List.of("会员", "积分", "优惠", "折扣"),
                "目前暂未开放会员制度，后续会推出积分和会员折扣活动，敬请期待！");

        addKnowledge("faq_allergy", "过敏原信息", List.of("过敏", "牛奶", "坚果", "成分"),
                "本店饮品含有牛奶、茶叶等成分。如有过敏需求请提前告知，我们可以调整配方。芋泥系列含有芋头，抹茶系列含有抹茶粉。");
    }

    public void addKnowledge(String id, String title, List<String> keywords, String content) {
        knowledgeBase.put(id, new KnowledgeEntry(id, title, keywords, content));
    }

    public void removeKnowledge(String id) {
        knowledgeBase.remove(id);
    }

    public List<KnowledgeEntry> search(String query) {
        String queryLower = query.toLowerCase();
        return knowledgeBase.values().stream()
                .filter(entry -> {
                    if (entry.title().toLowerCase().contains(queryLower)) return true;
                    if (entry.content().toLowerCase().contains(queryLower)) return true;
                    return entry.keywords().stream()
                            .anyMatch(kw -> queryLower.contains(kw.toLowerCase()));
                })
                .sorted((a, b) -> {
                    long scoreA = a.keywords().stream().filter(kw -> queryLower.contains(kw.toLowerCase())).count();
                    long scoreB = b.keywords().stream().filter(kw -> queryLower.contains(kw.toLowerCase())).count();
                    return Long.compare(scoreB, scoreA);
                })
                .collect(Collectors.toList());
    }

    public String getRelevantContext(String query) {
        List<KnowledgeEntry> results = search(query);
        if (results.isEmpty()) return "";
        return results.stream()
                .limit(3)
                .map(e -> "【" + e.title() + "】" + e.content())
                .collect(Collectors.joining("\n"));
    }

    public List<KnowledgeEntry> getAllKnowledge() {
        return List.copyOf(knowledgeBase.values());
    }

    public record KnowledgeEntry(String id, String title, List<String> keywords, String content) {}
}
