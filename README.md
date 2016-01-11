# PinyinTransform

一个可以完成汉字转拼音的分词器

如何在solr中使用:
```xml
 <fieldType name="text_pinyin" class="solr.TextField" positionIncrementGap="0">
        <analyzer type="index">
            <tokenizer isMaxWordLength="false" class="org.wltea.analyzer.lucene.IKTokenizerFactory"/>
            <filter class="com.lt.demo.lucene.analyzer.PinyinTransformTokenFilterFactory"
                    isOutChinese="true" charType="all" minTermLength="1"/>
            <filter class="solr.StopFilterFactory" ignoreCase="true"
                    words="stopwords.txt"/>
            <filter class="solr.LowerCaseFilterFactory"/>
            <filter class="solr.RemoveDuplicatesTokenFilterFactory"/>
        </analyzer>
        <analyzer type="query">
            <tokenizer isMaxWordLength="false" class="org.wltea.analyzer.lucene.IKTokenizerFactory"/>
            <filter class="solr.StopFilterFactory" ignoreCase="true"
                    words="stopwords.txt"/>
            <filter class="solr.LowerCaseFilterFactory"/>
        </analyzer>
    </fieldType>
 ```
 参数说明:
 isOutChinese:是否包含中文,true:包含

 charType:拼音转换类型,这里有三种,short:简拼,quan:全拼,all:简拼+全拼. 比如对"西游"进行拼音转换,charType="short",结果是xy;charTye="quan",结果是xiyou;charTye="all",结果是xiyou,xy

 minTermLength: 最小分词长度. 控制词的最小拼音转换长度,如果word.length<minTermLength,则不会对这个词进行处理

 maxTermLength: 最大分词长度. 控制词的最大拼音转换长度,如果word.length>maxTermLength,则会对这个词进行截断处理,以防止内存溢出问题.

 needEnglish: 输出是否包含英文,true:包含

