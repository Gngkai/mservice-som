package mkt.common.util;

import common.fnd.FndGlobal;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @date 2023/7/24 16:48
 * @action  排列组合工具类
 */
public class arrangeUtils {

    /**
     * 组合方法
     * @param str   进行组合的数组
     * @return 所有可能的组合
     */
    public static List<String> seqCombinate(String[] str) {
        List<String> combinations = new ArrayList<>();
        //组合字段的长度
        for (int size = 1; size <= str.length; size++) {
            //开始位置
            for (int start = 0; start < str.length; start++) {
                StringJoiner word = new StringJoiner(" ");
                //结束位置
                for (int end = start; end < size+start; end++) {
                    if (end>=str.length)
                        break;
                    word.add(str[end]);
                }
                if (!combinations.contains(word.toString())){
                    combinations.add(word.toString());
                }
            }
        }
        return combinations;
    }


    /**
     * 组合方法
     * @param str   进行组合的数组
     * @return 所有可能的组合
     */
    public static List<String> getCombinations(String[] str) {
        List<String> combinations = new ArrayList<>();
        combinationHelper("", str, 0, combinations);
        return combinations;
    }

    private static void combinationHelper(String prefix, String[] str, int start, List<String> combinations) {
        if (prefix!="")
            combinations.add(prefix);
        for (int i = start; i < str.length; i++) {
            if (FndGlobal.IsNotNull(prefix))
                combinationHelper(prefix+" "+ str[i], str, i + 1, combinations);
            else
                combinationHelper( str[i], str, i + 1, combinations);
        }
    }

    /**
     * 进行组合
     * @param array 组合数组
     * @return  所有可能的排序结果
     */
    public static List<List<String>> getArrange( String [] array){
        List<List<String>> permutations = new ArrayList<>();
        generatePermutations(array, 0, permutations);
        return permutations;
    }

    private static void generatePermutations(String[] array, int index, List<List<String>> permutations) {
        if (index == array.length - 1) {
            List<String> permutation = new ArrayList<>();
            for (String num : array) {
                permutation.add(num);
            }
            permutations.add(permutation);
        } else {
            for (int i = index; i < array.length; i++) {
                swap(array, index, i);
                generatePermutations(array, index + 1, permutations);
                swap(array, index, i); // 恢复数组顺序
            }
        }
    }
    // 交换数组元素
    public static void swap(String[] array, int i, int j) {
        String temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

}
