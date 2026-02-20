package com.mark.knowledge.agent.service;

import dev.langchain4j.agent.tool.Tool;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工具 Agent - 使用 @Tool 注解的标准方式
 *
 * 这是 LangChain4j 推荐的工具定义方式
 * 使用 exp4j 进行数学计算
 *
 * 同时提供公共方法供 IntelligentAgentRouter 直接调用
 *
 * @author mark
 */
@Component
public class ToolAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolAgent.class);

    /**
     * 计算工具 - 使用 @Tool 注解和 exp4j
     *
     * LLM 可以根据用户的请求自动调用这个工具
     * 仅用于基础数学计算，不处理金融计算
     */
    @Tool("【数学计算器】计算基础数学表达式，仅支持纯数学运算。支持：加减乘除、三角函数(sin/cos/tan)、根号(sqrt)等。不处理贷款、本金、利率、还款、月供、摊销、房贷、车贷等金融计算。如果用户询问贷款、还款、月供等，请使用 calculateAmortization 工具。")
    public String calculate(String expression) {
        log.info("LLM 调用计算工具: {}", expression);

        // 添加 null 检查
        if (expression == null || expression.trim().isEmpty()) {
            log.warn("计算工具收到空表达式");
            return " 无法识别的数学表达式。\n\n提示：请提供简单的数学计算，例如：1+1、2*3、sin(30) 等。";
        }

        try {
            // 检测是否为贷款计算（保留引导逻辑）
            if (isLoanCalculation(expression)) {
                log.warn("LLM 错误地选择了 calculate 工具进行贷款计算，应使用 calculateAmortization");
                return " 这是贷款摊销计算，不是基础数学计算。\n\n" +
                       "请使用专门的贷款计算工具：**calculateAmortization**\n\n" +
                       "**该工具可以计算：**\n" +
                       "- 每月还款额\n" +
                       "- 还款总额\n" +
                       "- 总利息\n" +
                       "- 完整的还款计划\n\n" +
                       "**使用示例：**\n" +
                       "- \"本金100000元，年利率5%，10年期\"\n" +
                       "- \"贷款100万，年利率3%，30年\"\n" +
                       "- \\\"房贷100万，利率3%，30年，等额本息\\\"";
            }

            // 提取数学表达式
            String mathExpression = extractMathExpression(expression);

            if (mathExpression.isEmpty() || mathExpression.length() < 3) {
                return " 无法识别的数学表达式。\n\n提示：请提供简单的数学计算，例如：1+1、2*3、sin(30) 等。";
            }

            // 使用 exp4j 计算表达式
            Expression exp = new ExpressionBuilder(mathExpression).build();
            double result = exp.evaluate();

            // 格式化结果
            String formattedResult = formatResult(result);

            String output = String.format("计算结果: %s = %s", mathExpression, formattedResult);
            log.info("计算结果: {}", output);

            return output;

        } catch (Exception e) {
            log.error("计算失败", e);

            // 提供更友好的错误提示
            return " 计算失败: " + e.getMessage() +
                   "\n\n提示：此工具仅支持基础数学计算。" +
                   "\n\n支持的运算：" +
                   "\n- 基本运算：+、-、*、/、^、" +
                   "\n- 函数：sin()、cos()、tan()、sqrt()、log() 等";
        }
    }

    /**
     * 检测是否为贷款/摊销计算请求
     */
    private boolean isLoanCalculation(String input) {
        // 防御性 null 检查
        if (input == null || input.isEmpty()) {
            return false;
        }

        // 明确的贷款关键词
        String[] loanKeywords = {
            "贷款", "摊销", "等额本息", "等额本金", "月供", "每期还款", "每月还", "还款"
        };

        for (String keyword : loanKeywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }

        // 检查：本金 + 利率 + 期限 的组合
        boolean hasPrincipal = input.contains("本金") || input.contains("金额");
        boolean hasRate = input.contains("利率") || input.contains("%");
        boolean hasTerm = input.contains("年") || input.contains("期") || input.contains("个月");

        // 如果同时有本金、利率和期限，极有可能是贷款计算
        if (hasPrincipal && hasRate && hasTerm) {
            return true;
        }

        return false;
    }

    /**
     * 天气查询工具
     */
    @Tool("查询指定城市的天气情况，需要提供城市名称")
    public String getWeather(String city) {
        log.info("LLM 调用天气工具: {}", city);

        if (city == null || city.trim().isEmpty()) {
            log.warn("天气工具收到空城市名称");
            return " 请提供城市名称，例如：北京、上海、广州等。";
        }

        String mockWeather = String.format("%s 的天气: 晴转多云，气温 15-25°C，微风", city);

        log.info("天气查询结果: {}", mockWeather);

        return mockWeather;
    }

    /**
     * 时间查询工具
     */
    @Tool("获取当前的日期和时间")
    public String getCurrentTime() {
        log.info("LLM 调用时间工具");

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE");
        String timeString = now.format(formatter);

        String output = "当前时间: " + timeString;

        log.info("时间查询结果: {}", output);

        return output;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 提取数学表达式
     */
    private String extractMathExpression(String input) {
        String expr = input.replaceAll("[^0-9+\\-*/^%().\\s]", " ")
                          .replaceAll("\\s+", " ")
                          .trim();

        expr = expr.replace("等于", "=")
                   .replace("是", "=")
                   .replace("?", "")
                   .replace("？", "")
                   .trim();

        if (expr.contains("=")) {
            expr = expr.substring(0, expr.indexOf("=")).trim();
        }

        return expr.isEmpty() ? "" : expr;
    }

    /**
     * 格式化计算结果
     */
    private String formatResult(double value) {
        // 如果是整数，返回整数形式
        if (value == (long) value) {
            return String.format("%d", (long) value);
        }

        // 否则保留适当的小数位数
        if (Math.abs(value) < 0.0001 || Math.abs(value) > 1000000) {
            return String.format("%.4e", value);
        } else {
            return String.format("%.4f", value).replaceAll("\\.?0+$", "");
        }
    }
}
