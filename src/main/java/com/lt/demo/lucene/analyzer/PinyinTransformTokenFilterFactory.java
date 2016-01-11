package com.lt.demo.lucene.analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import java.util.Map;

/**
 * 拼音转换分词过滤器工厂类
 *
 * @author ltao / 2015年8月24日
 */
public class PinyinTransformTokenFilterFactory extends TokenFilterFactory {

    private boolean isOutChinese = true; // 是否输出原中文开关
    private int minTermLength = 2; // 中文词组长度过滤，默认超过2位长度的中文才转换拼音
    private String charType = "all"; // 拼音缩写开关，输出编写时不输出全拼音
    private boolean needEnglish = true; //是否包含英文字母和数字
    private int maxTermLength = 50;


    /**
     * 构造器
     */
    public PinyinTransformTokenFilterFactory(Map<String, String> args) {
        super(args);
        this.isOutChinese = getBoolean(args, "isOutChinese", true);
        this.charType = get(args, "charType", "all");
        this.minTermLength = getInt(args, "minTermLength", 2);
        this.maxTermLength = getInt(args, "maxTermLength", 50);
        this.needEnglish = getBoolean(args, "needEnglish", true);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    public TokenFilter create(TokenStream input) {
        return new PinyinTransformTokenFilter(input, this.charType,
                this.minTermLength, this.maxTermLength, this.needEnglish,this.isOutChinese);
    }
}
