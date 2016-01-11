package com.lt.demo.lucene.analyzer;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.util.*;

/**
 * 拼音转换分词过滤器
 *
 * @author ltao / 2015年8月24日
 */
public class PinyinTransformTokenFilter extends TokenFilter {

    private Logger logger = Logger.getLogger(PinyinTransformTokenFilter.class);

    private boolean isOutChinese = true; // 是否输出原中文开关
    private String charType = "all"; // 拼音缩写开关，输出编写时不输出全拼音
    private int _minTermLength = 2; // 中文词组长度过滤，默认超过2位长度的中文才转换拼音
    private int _maxTermLength = 50;//最大词语长度，超过这个长度，会被做截断处理
    private boolean _needEnglish = true;//是否包含英文字母和数据
    private static final int MAX_POLYPHONE_COUNT = (int) Math.pow(2, 15);
    private HanyuPinyinOutputFormat outputFormat = new HanyuPinyinOutputFormat(); // 拼音转接输出格式

    private char[] curTermBuffer; // 底层词元输入缓存
    private int curTermLength; // 底层词元输入长度

    private final CharTermAttribute termAtt = (CharTermAttribute) addAttribute(CharTermAttribute.class); // 词元记录
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class); // 位置增量属性
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class); // 类型属性
    private boolean hasCurOut = false; // 当前输入是否已输出
    private Iterator<String> termIte = null; // 拼音结果集迭代器
    private boolean termIteRead = false; // 拼音结果集迭代器赋值后是否被读取标志
    private int inputTermPosInc = 1; // 输入Term的位置增量值

    /**
     * 构造器。默认长度超过2的中文词元进行转换，转换为全拼音且保留原中文词元
     *
     * @param input 词元输入
     */
    public PinyinTransformTokenFilter(TokenStream input) {
        this(input, 2, 50);
    }

    /**
     * 构造器。默认转换为全拼音且保留原中文词元
     *
     * @param input         词元输入
     * @param minTermLength 中文词组过滤长度
     */
    public PinyinTransformTokenFilter(TokenStream input, int minTermLength, int maxTermLength) {
        this(input, "all", minTermLength, maxTermLength);
    }

    /**
     * 构造器。默认长度超过2的中文词元进行转换，保留原中文词元
     *
     * @param input    词元输入
     * @param charType 输出拼音缩写还是完整拼音
     */
    public PinyinTransformTokenFilter(TokenStream input, String charType) {
        this(input, charType, 2, 50);
    }

    /**
     * 构造器。默认保留原中文词元
     *
     * @param input         词元输入
     * @param charType      输出拼音缩写还是完整拼音
     * @param minTermLength 中文词组过滤长度
     */
    public PinyinTransformTokenFilter(TokenStream input, String charType,
                                      int minTermLength, int maxTermLength) {
        this(input, charType, minTermLength, maxTermLength, true, true);
    }

    /**
     * 构造器
     *
     * @param input         词元输入
     * @param charType      输出拼音缩写还是完整拼音
     * @param minTermLength 中文词组过滤长度
     * @param isOutChinese  是否输入原中文词元
     */
    public PinyinTransformTokenFilter(TokenStream input, String charType,
                                      int minTermLength, int maxTermLength, boolean isOutChinese, boolean needEnglish) {
        super(input);
        this._minTermLength = minTermLength;
        this._maxTermLength = maxTermLength;
        if (this._minTermLength < 1) {
            this._minTermLength = 1;
        }
        if (this._maxTermLength < 1) {
            this._maxTermLength = 1;
        }
        this.isOutChinese = isOutChinese;
        this._needEnglish = needEnglish;
        this.outputFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        this.outputFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        this.charType = charType;
        addAttribute(OffsetAttribute.class); // 偏移量属性
    }

    /**
     * 判断字符串中是否含有中文
     *
     * @param s 待检测文本
     * @return 中文字符数
     */
    public static int chineseCharCount(String s) {
        int count = 0;
        if ((null == s) || ("".equals(s.trim()))) {
            return count;
        }
        for (int i = 0; i < s.length(); i++) {
            if (isChinese(s.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断字符是否是中文
     *
     * @param a 待测字符
     * @return 是 {@code true} ；否 {@code false}
     */
    public static boolean isChinese(char a) {
        return ((int) a >= 19968) && ((int) a <= 171941);
    }

    public static boolean isEnglishLetter(char a) {
        boolean isLetter = ((int) a >= 'a' && (int) a <= 'z') || ((int) a >= 'A' && (int) a <= 'Z');
        boolean isDigital = ((int) a >= '0' && (int) a <= '9');

        if (isLetter || isDigital) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * 分词过滤。<br/>
     * 该方法在上层调用中被循环调用，直到该方法返回false
     */
    public final boolean incrementToken() throws IOException {
        while (true) {
            if (this.curTermBuffer == null) { // 开始处理或上一输入词元已被处理完成
                if (!this.input.incrementToken()) { // 获取下一词元输入
                    return false; // 没有后继词元输入，处理完成，返回false，结束上层调用
                }
                // 缓存词元输入
                this.curTermBuffer = this.termAtt.buffer().clone();
                this.curTermLength = this.termAtt.length();
                this.inputTermPosInc = this.posIncrAtt.getPositionIncrement();
            }
            // 处理原输入词元
            if ((this.isOutChinese) && (!this.hasCurOut) && (this.termIte == null)) {
                // 准许输出原中文词元且当前没有输出原输入词元且还没有处理拼音结果集
                this.hasCurOut = true; // 标记以保证下次循环不会输出
                // 写入原输入词元
                this.termAtt.copyBuffer(this.curTermBuffer, 0,
                        this.curTermLength);
                this.posIncrAtt.setPositionIncrement(this.inputTermPosInc);
                return true; // 继续
            }
            String chinese = this.termAtt.toString();
            // 拼音处理
            int chineseCount = chineseCharCount(chinese);

            //有中文且符合长度限制
            if (chineseCount >= this._minTermLength) {
                //长度大于限制长度，做截断处理
                if (chineseCount > this._maxTermLength) {
                    chinese = chinese.substring(0, this._maxTermLength);
                }
                try {
                    // 输出拼音（缩写或全拼）
                    Collection<String> terms = null;
                    if ("all".equalsIgnoreCase(this.charType)) {
                        terms = getAll(chinese);
                    } else if ("short".equalsIgnoreCase(this.charType)) {
                        terms = getShort(chinese);
                    } else {
                        terms = getQuan(chinese);
                    }
                    if (terms != null) {
                        this.termIte = terms.iterator();
                        this.termIteRead = false;
                    }
                } catch (BadHanyuPinyinOutputFormatCombination badHanyuPinyinOutputFormatCombination) {
                    badHanyuPinyinOutputFormatCombination.printStackTrace();
                }


            }
            if (this.termIte != null) {
                if (this.termIte.hasNext()) { // 有拼音结果集且未处理完成
                    String pinyin = this.termIte.next();
                    this.termAtt.copyBuffer(pinyin.toCharArray(), 0, pinyin.length());
                    if (this.isOutChinese) {
                        this.posIncrAtt.setPositionIncrement(0);
                    } else {
                        this.posIncrAtt.setPositionIncrement(this.termIteRead ? 0 : this.inputTermPosInc);
                    }
                    if ("all".equalsIgnoreCase(this.charType)) {
                        this.typeAtt.setType("all_pinyin");
                    } else if ("short".equalsIgnoreCase(this.charType)) {
                        this.typeAtt.setType("short_pinyin");
                    } else {
                        this.typeAtt.setType("quan_pinyin");
                    }

                    this.termIteRead = true;
                    return true;
                }
            }
            // 没有中文或转换拼音失败，不用处理，
            // 清理缓存，下次取新词元
            this.curTermBuffer = null;
            this.termIte = null;
            this.hasCurOut = false; // 下次取词元后输出原词元（如果开关也准许）
        }
    }

    /**
     * 获取拼音缩写
     *
     * @param chinese 含中文的字符串，若不含中文，原样输出
     * @return 转换后的文本
     * @throws BadHanyuPinyinOutputFormatCombination
     */
    private Collection<String> getShort(String chinese)
            throws BadHanyuPinyinOutputFormatCombination {
        List<Set<String>> pinyinList = getPyList(chinese);
        boolean bigThanMaxPolyPhone = false;
        int size = 1;
        for (Set pinyin : pinyinList) {
            size = size * pinyin.size();
        }
        if (size > MAX_POLYPHONE_COUNT) {
            bigThanMaxPolyPhone = true;
        }
        Set<String> pinyins = getPyShort(pinyinList, bigThanMaxPolyPhone);
        return pinyins;
    }

    public void reset() throws IOException {
        super.reset();
    }

    /**
     * 获取拼音
     *
     * @param chinese 含中文的字符串，若不含中文，原样输出
     * @return 转换后的文本
     * @throws BadHanyuPinyinOutputFormatCombination
     */
    private Collection<String> getQuan(String chinese)
            throws BadHanyuPinyinOutputFormatCombination {
        List<Set<String>> pinyinList = getPyList(chinese);
        boolean bigThanMaxPolyPhone = false;
        int size = 1;
        for (Set pinyin : pinyinList) {
            size = size * pinyin.size();
        }
        if (size > MAX_POLYPHONE_COUNT) {
            bigThanMaxPolyPhone = true;
        }
        Set<String> pinyins = getPyQuan(pinyinList, bigThanMaxPolyPhone);
        return pinyins;
    }

    /**
     * 获取拼音
     *
     * @param chinese 含中文的字符串，若不含中文，原样输出
     * @return 转换后的文本
     * @throws BadHanyuPinyinOutputFormatCombination
     */
    private Collection<String> getAll(String chinese)
            throws BadHanyuPinyinOutputFormatCombination {

        Set<String> pinyinsAll = new HashSet<String>();
        List<Set<String>> pinyinList = getPyList(chinese);
        boolean bigThanMaxPolyPhone = false;
        int size = 1;
        for (Set pinyin : pinyinList) {
            size = size * pinyin.size();
        }
        if (size > MAX_POLYPHONE_COUNT) {
            bigThanMaxPolyPhone = true;
        }
        Set<String> pinyinQuans = getPyQuan(pinyinList, bigThanMaxPolyPhone);
        Set<String> pinyinShorts = getPyShort(pinyinList, bigThanMaxPolyPhone);
        if (pinyinQuans != null) {
            pinyinsAll.addAll(pinyinQuans);
        }
        if (pinyinShorts != null) {
            pinyinsAll.addAll(pinyinShorts);
        }
        pinyinQuans.clear();
        pinyinShorts.clear();
        return pinyinsAll;
    }

    private List<Set<String>> getPyList(String chinese) throws BadHanyuPinyinOutputFormatCombination {
        List<Set<String>> pinyinList = new ArrayList<Set<String>>();
        for (int i = 0; i < chinese.length(); i++) {
            char signalWord = chinese.charAt(i);
            String[] pinyinArray = null;
            HashSet<String> pinyinSet = null;
            if (isEnglishLetter(signalWord) && _needEnglish) {
                pinyinArray = new String[1];
                pinyinArray[0] = new String(Character.toString(signalWord));
            } else {
                pinyinArray = PinyinHelper.toHanyuPinyinStringArray(
                        signalWord, this.outputFormat);
            }
            if (pinyinArray != null && pinyinArray.length > 0) {
                pinyinSet = new HashSet<String>(Arrays.asList(pinyinArray));
                pinyinList.add(pinyinSet);
            }
        }
        return pinyinList;
    }

    private Set<String> getPyShort(List<Set<String>> pinyinList, boolean bigThanMaxPolyPhone) {
        Set<String> lstResult = new HashSet<String>();
        Set<String> lstNew = new HashSet<String>();
        for (Set<String> array : pinyinList) {
            if (lstNew.isEmpty()) {
                for (String charPinpin : array) {
                    lstNew.add(charPinpin.substring(0, 1));
                }
            } else {
                lstResult = lstNew;
                lstNew = new HashSet<String>();
                for (String pre : lstResult) {
                    int index = 0;
                    for (String charPinyin : array) {
                        if (bigThanMaxPolyPhone == true && index == 2) {
                            break;
                        } else {
                            lstNew.add(pre + charPinyin.substring(0, 1));
                            index++;
                        }

                    }
                }
                lstResult.clear();
                lstResult = lstNew;
            }
        }
        return lstResult;
    }

    private Set<String> getPyQuan(List<Set<String>> pinyinList, boolean bigThanMaxPolyPhone) {
        Set<String> lstResult = new HashSet<String>();
        Set<String> lstNew = new HashSet<String>();
        for (Set<String> array : pinyinList) {
            if (lstNew == null || lstNew.isEmpty()) {
                lstNew = new HashSet<String>();
                lstNew.addAll(array);
            } else {
                lstResult = lstNew;
                lstNew = new HashSet<String>();
                for (String pre : lstResult) {
                    int index = 0;
                    for (String charPinyin : array) {
                        if (bigThanMaxPolyPhone == true && index == 2) {
                            break;
                        } else {
                            lstNew.add(pre + charPinyin);
                            index++;
                        }
                    }
                }
                lstResult.clear();
                lstResult = lstNew;
            }

        }
        return lstResult;
    }
}
