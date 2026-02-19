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
    @Tool("计算基础数学表达式。仅支持: 1+1, 2*3, sin(30), sqrt(16) 等纯数学计算。不处理金融计算。")
    public String calculate(String expression) {
        log.info("LLM 调用计算工具: {}", expression);

        try {
            // 检测是否为金融计算请求
            if (isFinancialCalculation(expression)) {
                // 识别具体的金融计算类型
                if (expression.contains("摊销") || expression.contains("等额") || expression.contains("还款")) {
                    return "请使用专门的摊销计划计算工具。\n\n" +
                           "工具：calculateAmortization\n" +
                           "示例：本金100000元，年利率5%，10年期，每月等额本息还款";
                } else if (expression.contains("IRR") || expression.contains("内部收益率")) {
                    return "请使用专门的IRR计算工具。\n\n" +
                           "工具：calculateIRR\n" +
                           "示例：现金流 -10000,2500,2500,2500,2500";
                } else if (expression.contains("债券") && (expression.contains("价格") || expression.contains("定价"))) {
                    return "请使用专门的债券价格计算工具。\n\n" +
                           "工具：calculateBondPrice\n" +
                           "示例：面值1000元，票面利率5%，到期收益率4%，5年期";
                } else if (expression.contains("期权") || expression.contains("Black-Scholes")) {
                    return "请使用专门的期权定价工具。\n\n" +
                           "工具：calculateOptionPrice\n" +
                           "示例：标的价格100元，行权价105元，1年期，无风险利率3%，波动率25%";
                } else {
                    return "这是金融计算请求，请使用专门的金融工具：\n\n" +
                           "- 摊销计划：calculateAmortization\n" +
                           "- IRR计算：calculateIRR\n" +
                           "- 债券价格：calculateBondPrice\n" +
                           "- 期权定价：calculateOptionPrice\n" +
                           "- 债券久期：calculateBondDuration";
                }
            }

            // 提取数学表达式
            String mathExpression = extractMathExpression(expression);

            if (mathExpression.isEmpty() || mathExpression.length() < 3) {
                return "❌ 无法识别的数学表达式。\n\n提示：请提供简单的数学计算，例如：1+1、2*3、sin(30) 等。";
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
            return "❌ 计算失败: " + e.getMessage() +
                   "\n\n提示：此工具仅支持基础数学计算。" +
                   "\n\n支持的运算：" +
                   "\n- 基本运算：+、-、*、/、^、" +
                   "\n- 函数：sin()、cos()、tan()、sqrt()、log() 等";
        }
    }

    /**
     * 检测是否为金融计算请求
     */
    private boolean isFinancialCalculation(String input) {
        // 优先检测明确的金融计算类型
        String[] strongFinancialIndicators = {
            "摊销", "等额本息", "等额本金", "月供", "每期还款",
            "IRR", "内部收益率", "NPV", "净现值",
            "YTM", "到期收益率", "久期", "凸度",
            "Black-Scholes", "期权定价", "行权价"
        };

        // 检测强金融指标
        for (String indicator : strongFinancialIndicators) {
            if (input.contains(indicator)) {
                return true;
            }
        }

        // 检测是否有本金 + 利率/期限的组合（更精确的判断）
        boolean hasPrincipal = input.contains("本金") || input.contains("贷款") || input.contains("金额");
        boolean hasRate = input.contains("利率") || input.contains("%") || input.contains("年利率");
        boolean hasTerm = input.contains("年") || input.contains("期") || input.contains("月");
        boolean hasRepayment = input.contains("还款") || input.contains("摊销") || input.contains("供");

        // 如果同时有本金和利率/期限，很可能是金融计算
        if (hasPrincipal && (hasRate || hasTerm)) {
            return true;
        }

        // 检测债券相关
        boolean hasBond = input.contains("债券") || input.contains("票面");
        boolean hasBondSpecific = input.contains("收益率") || input.contains("面值") || input.contains("到期");
        if (hasBond && hasBondSpecific) {
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

    // ========== 公共方法，供 IntelligentAgentRouter 直接调用 ==========

    /**
     * 公共计算方法 - 供 IntelligentAgentRouter 调用
     */
    public String calculatePublic(String expression) {
        return calculate(expression);
    }

    /**
     * 公共天气查询方法 - 供 IntelligentAgentRouter 调用
     */
    public String getWeatherPublic(String city) {
        return getWeather(city);
    }

    /**
     * 公共时间查询方法 - 供 IntelligentAgentRouter 调用
     */
    public String getCurrentTimePublic(String query) {
        return getCurrentTime();
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
