package com.lt.demo.lucene.analyzer;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

public class TestPinyinTransformTokenFilter extends BaseTokenStreamTestCase {

    private MockTokenizer tokenizer;
    private PinyinTransformTokenFilter filter;

    @Before
    public void before() throws IOException {
        this.tokenizer = new MockTokenizer();
        this.tokenizer.setReader(new StringReader("西游"));
    }


    @Test
    public void testFullWithNoChineseOut() throws IOException {
        this.filter = new PinyinTransformTokenFilter(tokenizer, "all", 1, 10, true, true);
        this.filter.reset();
        int position = 0;
        System.out.println("所有：");
        while (this.filter.incrementToken()) {
            CharTermAttribute termAtt = this.filter.getAttribute(CharTermAttribute.class);
            String token = termAtt.toString();
            int increment = this.filter.getAttribute(PositionIncrementAttribute.class).getPositionIncrement();
            position += increment;
            OffsetAttribute offset = this.filter.getAttribute(OffsetAttribute.class);
            TypeAttribute type = this.filter.getAttribute(TypeAttribute.class);
            System.out.println(position + "[" + offset.startOffset() + "," + offset.endOffset() + "} (" + type
                    .type() + ") " + token);
        }
    }

    @Test
    public void testShort() throws IOException {
        this.filter = new PinyinTransformTokenFilter(tokenizer, "short", 1, 10, true, false);
        this.filter.reset();
        int position = 0;
        System.out.println("简拼：");
        while (this.filter.incrementToken()) {
            CharTermAttribute termAtt = this.filter.getAttribute(CharTermAttribute.class);
            String token = termAtt.toString();
            int increment = this.filter.getAttribute(PositionIncrementAttribute.class).getPositionIncrement();
            position += increment;
            OffsetAttribute offset = this.filter.getAttribute(OffsetAttribute.class);
            TypeAttribute type = this.filter.getAttribute(TypeAttribute.class);
            System.out.println(position + "[" + offset.startOffset() + "," + offset.endOffset() + "} (" + type
                    .type() + ") " + token);
        }
    }


    @Test
    public void testQuan() throws IOException {
        this.filter = new PinyinTransformTokenFilter(tokenizer, "quan", 1, 10, true, false);
        this.filter.reset();
        int position = 0;
        System.out.println("全：");
        while (this.filter.incrementToken()) {
            CharTermAttribute termAtt = this.filter.getAttribute(CharTermAttribute.class);
            String token = termAtt.toString();
            int increment = this.filter.getAttribute(PositionIncrementAttribute.class).getPositionIncrement();
            position += increment;
            OffsetAttribute offset = this.filter.getAttribute(OffsetAttribute.class);
            TypeAttribute type = this.filter.getAttribute(TypeAttribute.class);
            System.out.println(position + "[" + offset.startOffset() + "," + offset.endOffset() + "} (" + type
                    .type() + ") " + token);
        }
    }
}