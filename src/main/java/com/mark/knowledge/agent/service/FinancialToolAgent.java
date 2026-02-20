package com.mark.knowledge.agent.service;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 金融工具 Agent
 *
 * 提供专业的金融计算工具，使用 KaTeX 渲染 LaTeX 公式
 *
 * @author mark
 */
@Component
public class FinancialToolAgent {

    private static final Logger log = LoggerFactory.getLogger(FinancialToolAgent.class);

    private final FinancialCalculationService financialCalcService;
    private final BondCalculationService bondCalcService;
    private final OptionCalculationService optionCalcService;

    public FinancialToolAgent(
            FinancialCalculationService financialCalcService,
            BondCalculationService bondCalcService,
            OptionCalculationService optionCalcService) {
        this.financialCalcService = financialCalcService;
        this.bondCalcService = bondCalcService;
        this.optionCalcService = optionCalcService;
    }

    /**
     * 计算内部收益率 (IRR)
     *
     * 使用 LLM + Tool Calling + 金融计算库 的混合架构
     */
    @Tool("计算投资内部收益率IRR。参数：现金流数组（逗号分隔），第一个是初始投资（负数），后面是各期回报。例如：-10000,2500,2500,2500,2500,2500")
    public String calculateIRR(String cashFlowStr) {

        log.info("LLM 提取参数 - 现金流: {}", cashFlowStr);

        try {
            // 步骤1: 解析现金流
            String[] parts = cashFlowStr.split(",");
            double[] cashFlows = new double[parts.length];

            for (int i = 0; i < parts.length; i++) {
                cashFlows[i] = Double.parseDouble(parts[i].trim());
            }

            log.info("解析后参数 - 现金流数组: {}", java.util.Arrays.toString(cashFlows));

            // 步骤2: 参数验证
            if (cashFlows.length < 2) {
                return buildError("现金流至少需要2个数据点", "格式示例：-1000,200,200,200,200");
            }

            // 步骤3: 使用金融计算库计算 IRR
            Double irr = financialCalcService.calculateIRR(cashFlows);

            // 步骤4: 构建结果（包含公式）
            return buildIRRResult(cashFlows, irr);

        } catch (NumberFormatException e) {
            log.error("参数解析失败", e);
            return buildError("现金流格式错误",
                    "请提供逗号分隔的数字，例如：-1000,200,200,200,200");
        } catch (Exception e) {
            log.error("IRR 计算失败", e);
            return buildError("IRR 计算失败: " + e.getMessage(),
                    "请确保现金流格式正确，第一个应该是负数（初始投资）");
        }
    }

    /**
     * 构建 IRR 计算结果
     */
    private String buildIRRResult(double[] cashFlows, Double irr) {
        StringBuilder result = new StringBuilder();

        result.append("## 📈 内部收益率 (IRR) 计算\n\n");

        result.append("### 现金流分析\n");
        for (int i = 0; i < cashFlows.length; i++) {
            String type = i == 0 ? "初始投资 (CF₀)" : "第" + i + "期 (CF" + i + ")";
            String amount = cashFlows[i] < 0 ?
                    String.format("-%,.2f", -cashFlows[i]) :
                    String.format("+,.2f", cashFlows[i]);
            result.append(String.format("- %s: **%s 元**\n", type, amount));
        }

        result.append("\n### IRR 计算公式\n");
        result.append("**净现值方程：**\n");
        result.append("$$\n");
        result.append("\\sum_{t=0}^{n} \\frac{CF_t}{(1+IRR)^t} = 0\n");
        result.append("$$\n\n");
        result.append("其中：\n");
        result.append("- $CF_t$ = 第 $t$ 期的现金流\n");
        result.append("- $IRR$ = 内部收益率（使 NPV = 0 的折现率）\n");
        result.append("- $n$ = 现金流期数\n\n");

        result.append("### 计算结果\n");
        result.append(String.format("- **IRR (内部收益率)**: **%.2f%%**\n\n", irr * 100));

        result.append("### 💡 投资建议\n");
        if (irr > 0.10) {
            result.append(String.format("✅ 优秀的投资机会！IRR 为 **%.2f%%**，远高于一般投资回报率\n", irr * 100));
        } else if (irr > 0.05) {
            result.append(String.format("✅ 良好的投资机会，IRR 为 **%.2f%%**\n", irr * 100));
        } else if (irr > 0) {
            result.append(String.format("⚠️  IRR 为 **%.2f%%**，请与您的资金成本比较\n", irr * 100));
        } else {
            result.append("❌ 负的IRR，该项目不可行\n");
        }
        result.append("\n**决策标准**：IRR > 资金成本 → 接受项目；IRR < 资金成本 → 拒绝项目");

        return result.toString();
    }

    /**
     * 计算债券价格
     *
     * 使用 LLM + Tool Calling + 金融计算库 的混合架构
     */
    @Tool("计算债券价格。参数：1)面值（如1000或1000元）2)票面利率（如0.05或5%）3)到期收益率YTM（如0.04或4%）4)期限年数（如5或5年）")
    public String calculateBondPrice(String faceValue, String couponRate, String yield, String years) {

        log.info("LLM 提取参数 - 面值: {}, 票面利率: {}, YTM: {}, 期限: {} 年", faceValue, couponRate, yield, years);

        try {
            // 步骤1: 解析参数
            double faceValueNum = parseAmount(faceValue);
            double couponRateNum = parseRate(couponRate);
            double yieldNum = parseRate(yield);
            double yearsNum = parseTermAsDouble(years);
            int frequency = 2; // 默认半年付息

            log.info("解析后参数 - 面值: {} 元, 票面利率: {}%, YTM: {}%, 期限: {} 年",
                    faceValueNum, couponRateNum * 100, yieldNum * 100, yearsNum);

            // 步骤2: 参数验证
            if (faceValueNum <= 0) {
                return buildError("面值必须大于0", "请提供有效的债券面值，例如：1000");
            }
            if (couponRateNum < 0 || couponRateNum > 1) {
                return buildError("票面利率必须在 0%-100% 之间", "例如：0.05 或 5%");
            }
            if (yieldNum < 0 || yieldNum > 1) {
                return buildError("到期收益率必须在 0%-100% 之间", "例如：0.04 或 4%");
            }
            if (yearsNum <= 0 || yearsNum > 100) {
                return buildError("期限必须在 1-100 年之间", "例如：5 或 5年");
            }

            // 步骤3: 使用金融计算库计算债券价格
            double price = bondCalcService.calculateBondPrice(
                    faceValueNum, couponRateNum, yieldNum, yearsNum, frequency);

            // 步骤4: 构建结果（包含公式）
            return buildBondPriceResult(faceValueNum, couponRateNum, yieldNum, yearsNum, frequency, price);

        } catch (NumberFormatException e) {
            log.error("参数解析失败", e);
            return buildError("参数格式错误",
                    "请检查参数格式：\n" +
                    "- 面值：1000\n" +
                    "- 票面利率：0.05 或 5%\n" +
                    "- 到期收益率：0.04 或 4%\n" +
                    "- 期限：5 或 5年");
        } catch (Exception e) {
            log.error("债券价格计算失败", e);
            return buildError("计算失败: " + e.getMessage(), "请提供完整的债券参数");
        }
    }

    /**
     * 构建债券价格计算结果
     */
    private String buildBondPriceResult(double faceValue, double couponRate, double yield,
                                       double years, int frequency, double price) {
        double pricePercent = (price / faceValue) * 100;
        String status = price > faceValue ? "溢价交易（价格 > 面值）" :
                       price < faceValue ? "折价交易（价格 < 面值）" :
                       "平价交易（价格 = 面值）";

        StringBuilder result = new StringBuilder();

        result.append("## 💰 债券价格计算\n\n");

        result.append("### 债券参数\n");
        result.append(String.format("- 面值: **%,.2f 元**\n", faceValue));
        result.append(String.format("- 票面利率: **%.2f%%**\n", couponRate * 100));
        result.append(String.format("- 到期收益率 (YTM): **%.2f%%**\n", yield * 100));
        result.append(String.format("- 到期年限: **%.1f 年**\n", years));
        result.append(String.format("- 付息频率: 每年 **%d** 次\n\n", frequency));

        result.append("### 定价公式\n");
        result.append("**债券现值公式：**\n");
        result.append("$$\n");
        result.append("P = \\sum_{t=1}^{n \\times m} \\frac{C/m}{(1+y/m)^t} + \\frac{F}{(1+y/m)^{n \\times m}}\n");
        result.append("$$\n\n");
        result.append("其中：\n");
        result.append("- $P$ = 债券价格\n");
        result.append("- $C$ = 年票息（面值 × 票面利率）\n");
        result.append("- $F$ = 面值\n");
        result.append("- $y$ = 年到期收益率 (YTM)\n");
        result.append("- $m$ = 每年付息次数\n");
        result.append(String.format("- $n$ = 年限 = %.1f 年\n\n", years));

        result.append("### 计算结果\n");
        result.append(String.format("- **债券价格**: %,.2f 元\n", price));
        result.append(String.format("- **价格百分比**: %.2f%%\n", pricePercent));
        result.append(String.format("- **状态**: %s\n\n", status));

        result.append("### 💡 说明\n");
        if (price > faceValue) {
            result.append("- 债券溢价交易，因为 **票面利率 > 到期收益率**\n");
        } else if (price < faceValue) {
            result.append("- 债券折价交易，因为 **票面利率 < 到期收益率**\n");
        } else {
            result.append("- 债券平价交易，因为 **票面利率 = 到期收益率**\n");
        }

        return result.toString();
    }

    /**
     * 解析期限（返回 double 类型，用于债券计算）
     */
    private double parseTermAsDouble(String term) {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("期限不能为空");
        }

        String cleaned = term.replace("年", "").replace("期限", "")
                              .replace("期", "").trim();

        return Double.parseDouble(cleaned);
    }

    /**
     * 计算期权价格 (Black-Scholes)
     *
     * 使用 LLM + Tool Calling + 金融计算库 的混合架构
     */
    @Tool("计算期权价格(Black-Scholes模型)。参数：1)标的价格（如100）2)行权价（如105）3)期限年数（如1或1年）4)无风险利率（如0.03或3%）5)波动率（如0.25或25%）")
    public String calculateOptionPrice(String spotPrice, String strikePrice, String timeToMaturity,
                                      String riskFreeRate, String volatility) {

        log.info("LLM 提取参数 - 标的: {}, 行权: {}, 期限: {} 年, 利率: {}, 波动率: {}",
                spotPrice, strikePrice, timeToMaturity, riskFreeRate, volatility);

        try {
            // 步骤1: 解析参数
            double spotNum = parseAmount(spotPrice);
            double strikeNum = parseAmount(strikePrice);
            double timeNum = parseTermAsDouble(timeToMaturity);
            double rateNum = parseRate(riskFreeRate);
            double volNum = parseRate(volatility); // 波动率也是百分比

            // 默认为看涨期权，除非用户明确指定看跌
            boolean isCall = true; // 可以通过额外的参数来控制

            log.info("解析后参数 - 标的: {} 元, 行权: {} 元, 期限: {} 年, 利率: {}%, 波动率: {}%",
                    spotNum, strikeNum, timeNum, rateNum * 100, volNum * 100);

            // 步骤2: 参数验证
            if (spotNum <= 0 || strikeNum <= 0) {
                return buildError("标的价格和行权价必须大于0", "请提供有效的价格，例如：100、105");
            }
            if (timeNum <= 0 || timeNum > 50) {
                return buildError("期限必须在 0-50 年之间", "例如：1 或 1年");
            }
            if (rateNum < 0 || rateNum > 1) {
                return buildError("无风险利率必须在 0%-100% 之间", "例如：0.03 或 3%");
            }
            if (volNum <= 0 || volNum > 5) {
                return buildError("波动率必须在 0%-500% 之间", "例如：0.25 或 25%");
            }

            // 步骤3: 使用金融计算库计算期权价格和Greeks
            double price = optionCalcService.calculateOptionPrice(
                    spotNum, strikeNum, timeNum, rateNum, volNum, isCall);

            double delta = optionCalcService.calculateDelta(spotNum, strikeNum,
                    timeNum, rateNum, volNum, isCall);
            double gamma = optionCalcService.calculateGamma(spotNum, strikeNum,
                    timeNum, rateNum, volNum);
            double vega = optionCalcService.calculateVega(spotNum, strikeNum,
                    timeNum, rateNum, volNum);
            double theta = optionCalcService.calculateTheta(spotNum, strikeNum,
                    timeNum, rateNum, volNum, isCall);
            double rho = optionCalcService.calculateRho(spotNum, strikeNum,
                    timeNum, rateNum, volNum, isCall);

            // 步骤4: 构建结果（包含公式）
            return buildOptionPriceResult(spotNum, strikeNum, timeNum, rateNum, volNum,
                    isCall, price, delta, gamma, vega, theta, rho);

        } catch (NumberFormatException e) {
            log.error("参数解析失败", e);
            return buildError("参数格式错误",
                    "请检查参数格式：\n" +
                    "- 标的价格：100\n" +
                    "- 行权价：105\n" +
                    "- 期限：1 或 1年\n" +
                    "- 无风险利率：0.03 或 3%\n" +
                    "- 波动率：0.25 或 25%");
        } catch (Exception e) {
            log.error("期权价格计算失败", e);
            return buildError("计算失败: " + e.getMessage(), "请提供完整的期权参数");
        }
    }

    /**
     * 构建期权价格计算结果
     */
    private String buildOptionPriceResult(double spotPrice, double strikePrice, double timeToMaturity,
                                         double riskFreeRate, double volatility, boolean isCall,
                                         double price, double delta, double gamma, double vega,
                                         double theta, double rho) {
        String optionType = isCall ? "看涨期权 (Call)" : "看跌期权 (Put)";

        StringBuilder result = new StringBuilder();

        result.append("## 📈 Black-Scholes 期权定价\n\n");

        result.append("### 期权参数\n");
        result.append("- 期权类型: **" + optionType + "**\n");
        result.append(String.format("- 标的资产价格 ($S$): **%,.2f 元**\n", spotPrice));
        result.append(String.format("- 行权价格 ($K$): **%,.2f 元**\n", strikePrice));
        result.append(String.format("- 到期时间 ($T$): **%.2f 年**\n", timeToMaturity));
        result.append(String.format("- 无风险利率 ($r$): **%.2f%%**\n", riskFreeRate * 100));
        result.append(String.format("- 波动率 ($\\sigma$): **%.2f%%**\n\n", volatility * 100));

        result.append("### Black-Scholes 公式\n");
        result.append("**看涨期权定价公式：**\n");
        result.append("$$\n");
        result.append("C = S \\cdot N(d_1) - K \\cdot e^{-rT} \\cdot N(d_2)\n");
        result.append("$$\n\n");
        result.append("其中：\n");
        result.append("$$\n");
        result.append("d_1 = \\frac{\\ln(S/K) + (r + \\sigma^2/2)T}{\\sigma\\sqrt{T}}\n");
        result.append("$$\n");
        result.append("$$\n");
        result.append("d_2 = d_1 - \\sigma\\sqrt{T}\n");
        result.append("$$\n\n");
        result.append("- $S$ = 标的资产价格\n");
        result.append("- $K$ = 行权价格\n");
        result.append("- $r$ = 无风险利率\n");
        result.append("- $T$ = 到期时间（年）\n");
        result.append("- $\\sigma$ = 波动率\n");
        result.append("- $N(\\cdot)$ = 标准正态分布的累积分布函数\n\n");

        result.append("### 期权价格\n");
        result.append(String.format("- **期权价格**: %,.4f 元\n\n", price));

        result.append("### Greeks 风险指标\n");
        result.append(String.format("- **Delta ($\\Delta$)**: %.4f %s\n", delta,
            Math.abs(delta) > 0.7 ? "(深度实值)" : Math.abs(delta) < 0.3 ? "(深度虚值)" : "(平值附近)"));
        result.append(String.format("- **Gamma ($\\Gamma$)**: %.4f - Delta 对标的价格敏感度\n", gamma));
        result.append(String.format("- **Vega ($\\nu$)**: %.4f - 波动率变动1%%, 期权价格变动 %.4f 元\n", vega, vega));
        result.append(String.format("- **Theta ($\\Theta$)**: %.6f - 时间流逝1天, 期权价格变动 %.6f 元\n", theta, theta));
        result.append(String.format("- **Rho ($\\rho$)**: %.4f - 利率变动1%%, 期权价格变动 %.4f 元\n\n", rho, rho));

        result.append("### 💡 Greeks 解释\n");
        result.append(String.format("- **Delta**: 标的价格变动1元, 期权价格变动 %.4f 元\n", delta));
        result.append("- **Gamma**: Delta 的敏感度，反映对冲风险\n");
        result.append(String.format("- **Vega**: 波动率变动1%%, 期权价格变动 %.4f 元\n", vega));
        result.append(String.format("- **Theta**: 时间流逝1天, 期权价格变动 %.6f 元\n", theta));
        result.append(String.format("- **Rho**: 利率变动1%%, 期权价格变动 %.4f 元\n", rho));

        return result.toString();
    }

    /**
     * 计算债券久期和凸度
     *
     * 使用 LLM + Tool Calling + 金融计算库 的混合架构
     */
    @Tool("计算债券久期和凸度。参数：1)面值（如1000或1000元）2)票面利率（如0.05或5%）3)到期收益率YTM（如0.04或4%）4)期限年数（如5或5年）")
    public String calculateBondDuration(String faceValue, String couponRate, String yield, String years) {

        log.info("LLM 提取参数 - 面值: {}, 票面利率: {}, YTM: {}, 期限: {} 年", faceValue, couponRate, yield, years);

        try {
            // 步骤1: 解析参数
            double faceValueNum = parseAmount(faceValue);
            double couponRateNum = parseRate(couponRate);
            double yieldNum = parseRate(yield);
            double yearsNum = parseTermAsDouble(years);
            int frequency = 2; // 默认半年付息

            log.info("解析后参数 - 面值: {} 元, 票面利率: {}%, YTM: {}%, 期限: {} 年",
                    faceValueNum, couponRateNum * 100, yieldNum * 100, yearsNum);

            // 步骤2: 参数验证
            if (faceValueNum <= 0) {
                return buildError("面值必须大于0", "请提供有效的债券面值，例如：1000");
            }
            if (couponRateNum < 0 || couponRateNum > 1) {
                return buildError("票面利率必须在 0%-100% 之间", "例如：0.05 或 5%");
            }
            if (yieldNum < 0 || yieldNum > 1) {
                return buildError("到期收益率必须在 0%-100% 之间", "例如：0.04 或 4%");
            }
            if (yearsNum <= 0 || yearsNum > 100) {
                return buildError("期限必须在 1-100 年之间", "例如：5 或 5年");
            }

            // 步骤3: 使用金融计算库计算债券价格、久期和凸度
            double price = bondCalcService.calculateBondPrice(
                    faceValueNum, couponRateNum, yieldNum, yearsNum, frequency);

            double macaulayDuration = bondCalcService.calculateMacaulayDuration(
                    price, faceValueNum, couponRateNum, yieldNum, yearsNum, frequency);

            double modifiedDuration = bondCalcService.calculateModifiedDuration(
                    macaulayDuration, yieldNum, frequency);

            double convexity = bondCalcService.calculateConvexity(
                    price, faceValueNum, couponRateNum, yieldNum, yearsNum, frequency);

            // 步骤4: 构建结果（包含公式）
            return buildBondDurationResult(faceValueNum, couponRateNum, yieldNum, yearsNum, frequency,
                    macaulayDuration, modifiedDuration, convexity);

        } catch (NumberFormatException e) {
            log.error("参数解析失败", e);
            return buildError("参数格式错误",
                    "请检查参数格式：\n" +
                    "- 面值：1000\n" +
                    "- 票面利率：0.05 或 5%\n" +
                    "- 到期收益率：0.04 或 4%\n" +
                    "- 期限：5 或 5年");
        } catch (Exception e) {
            log.error("久期计算失败", e);
            return buildError("计算失败: " + e.getMessage(), "请提供完整的债券参数");
        }
    }

    /**
     * 构建债券久期计算结果
     */
    private String buildBondDurationResult(double faceValue, double couponRate, double yield,
                                         double years, int frequency,
                                         double macaulayDuration, double modifiedDuration,
                                         double convexity) {
        StringBuilder result = new StringBuilder();

        result.append("## 📊 债券久期分析\n\n");

        result.append("### 债券参数\n");
        result.append(String.format("- 面值: **%,.2f 元**\n", faceValue));
        result.append(String.format("- 票面利率: **%.2f%%**\n", couponRate * 100));
        result.append(String.format("- 到期收益率 (YTM): **%.2f%%**\n", yield * 100));
        result.append(String.format("- 到期年限: **%.1f 年**\n", years));
        result.append(String.format("- 付息频率: 每年 **%d** 次\n\n", frequency));

        result.append("### 计算结果\n");
        result.append(String.format("- **Macaulay 久期**: %.2f 年\n", macaulayDuration));
        result.append(String.format("- **修正久期**: %.4f\n", modifiedDuration));
        result.append(String.format("- **凸度**: %.4f\n\n", convexity));

        result.append("### 公式说明\n");
        result.append("**Macaulay 久期（加权平均回收期）：**\n");
        result.append("$$\n");
        result.append("D_{mac} = \\frac{\\sum_{t=1}^{n} \\frac{t \\cdot CF_t}{(1+y/m)^t}}{P}\n");
        result.append("$$\n\n");
        result.append("其中：\n");
        result.append("- $CF_t$ = 第 $t$ 期现金流\n");
        result.append("- $y$ = 年到期收益率\n");
        result.append("- $m$ = 每年付息次数\n");
        result.append("- $P$ = 债券价格\n\n");

        result.append("**修正久期（利率敏感度）：**\n");
        result.append("$$\n");
        result.append("D_{mod} = \\frac{D_{mac}}{1 + y/m}\n");
        result.append("$$\n\n");
        result.append("**含义**：修正久期表示利率变动 1%，债券价格约变动的百分比\n\n");

        result.append("**凸度（价格-收益率关系的曲率）：**\n");
        result.append("$$\n");
        result.append("凸度 = \\frac{1}{P} \\cdot \\frac{\\partial^2 P}{\\partial y^2}\n");
        result.append("$$\n\n");
        result.append("**含义**：凸度衡量久期随利率变化的速度\n\n");

        result.append("### 💡 解释\n");
        result.append(String.format("- 修正久期 **%.4f** 表示利率变动 **1%%**, 债券价格约变动 **%.4f%%**\n",
                modifiedDuration, modifiedDuration * 100));
        result.append("- 凸度为正说明债券价格随利率下降的幅度大于随利率上升的幅度");
        result.append("- 凸度越大，利率风险越小");

        return result.toString();
    }

    /**
     * 计算贷款摊销计划
     *
     * 使用 LLM + Tool Calling + 金融计算库 的混合架构
     * - LLM 负责理解用户意图和提取参数
     * - 代码负责参数解析和调用金融计算库
     * - 金融计算库提供精确的计算公式
     *
     * 参数说明：
     * - principal: 贷款本金，支持格式：100000 或 10万 或 十万
     * - annualRate: 年利率，支持格式：0.05 或 5% 或 百分之五
     * - loanTerm: 贷款期限（年），支持格式：10 或 10年
     */
    @Tool("【贷款计算器】计算房贷、车贷、个人贷款的每月还款额（等额本息）。适用场景：用户提到贷款、本金、利率、期限、还款、月供、摊销、等额本息、房贷、车贷等关键词，询问每月还款、月供、每期还款等。参数：1)贷款本金（如100000或10万）2)年利率（如0.05或5%）3)贷款期限年数（如10或10年）。返回：每月还款额、还款总额、总利息、完整还款计划表、计算公式。")
    public String calculateAmortization(String principal, String annualRate, String loanTerm) {

        log.info("LLM 提取参数 - 本金: {}, 利率: {}, 期限: {} 年", principal, annualRate, loanTerm);

        // 添加 null 检查
        if (principal == null || annualRate == null || loanTerm == null) {
            log.warn("贷款计算工具收到空参数");
            return " **缺少必要参数**\n\n" +
                   "贷款计算需要以下三个参数：\n" +
                   "1. **贷款本金**：例如 100000 或 10万\n" +
                   "2. **年利率**：例如 0.05 或 5%\n" +
                   "3. **贷款期限**：例如 10 或 10年\n\n" +
                   "请提供完整的贷款信息，我会帮您计算每月还款额。";
        }

        try {
            // 步骤1: 解析 LLM 提取的参数（处理可能的中文单位和格式）
            double principalValue = parseAmount(principal);
            double rateValue = parseRate(annualRate);
            int yearsValue = parseTerm(loanTerm);
            int frequency = 12; // 默认每月还款

            log.info("解析后参数 - 本金: {} 元, 利率: {}%, 期限: {} 年",
                    principalValue, rateValue * 100, yearsValue);

            // 步骤2: 参数验证
            if (principalValue <= 0) {
                return buildError("本金必须大于0", "请提供有效的贷款本金，例如：100000 或 10万");
            }
            if (rateValue <= 0 || rateValue > 1) {
                return buildError("利率必须在 0%-100% 之间", "请提供有效的年利率，例如：0.05 或 5%");
            }
            if (yearsValue <= 0 || yearsValue > 50) {
                return buildError("期限必须在 1-50 年之间", "请提供有效的贷款期限，例如：10 或 10年");
            }

            // 步骤3: 使用金融计算库进行精确计算
            FinancialCalculationService.AmortizationSchedule[] schedule =
                    financialCalcService.calculateAmortization(principalValue, rateValue, yearsValue, frequency);

            // 步骤4: 格式化返回结果
            double monthlyRate = rateValue / frequency;
            int totalPayments = yearsValue * frequency;
            double monthlyPayment = schedule[0].payment();

            return buildAmortizationResult(principalValue, rateValue, yearsValue, frequency,
                    monthlyRate, totalPayments, monthlyPayment, schedule);

        } catch (NumberFormatException e) {
            log.error("参数解析失败", e);
            return buildError("参数格式错误",
                    "请检查参数格式：\n" +
                    "- 本金：100000 或 10万\n" +
                    "- 利率：0.05 或 5%\n" +
                    "- 期限：10 或 10年");
        } catch (Exception e) {
            log.error("摊销计划计算失败", e);
            return buildError("计算失败: " + e.getMessage(),
                    "请提供完整参数：\n" +
                    "- 贷款本金（单位：元）\n" +
                    "- 年利率（如 0.05 表示 5%）\n" +
                    "- 贷款期限（年）");
        }
    }

    // ========== 新的参数解析方法（LLM + Tool Calling 架构） ==========

    /**
     * 解析金额
     * 支持格式：100000, 10万, 十万, ¥100,000
     */
    private double parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("金额不能为空");
        }

        // 移除常见的货币符号和逗号
        String cleaned = amount.replace("¥", "").replace("￥", "")
                              .replace(",", "").replace("，", "")
                              .replace("元", "").trim();

        // 处理中文数字（简单映射）
        if (cleaned.contains("十万")) {
            return 100000;
        } else if (cleaned.contains("百万")) {
            return 1000000;
        } else if (cleaned.contains("千万")) {
            return 10000000;
        }

        // 处理单位：万、千
        if (cleaned.contains("万")) {
            String numberPart = cleaned.replace("万", "").trim();
            double value = parseChineseNumber(numberPart);
            return value * 10000;
        } else if (cleaned.contains("千")) {
            String numberPart = cleaned.replace("千", "").trim();
            double value = parseChineseNumber(numberPart);
            return value * 1000;
        }

        // 纯数字
        return parseChineseNumber(cleaned);
    }

    /**
     * 解析利率
     * 支持格式：0.05, 5%, 百分之五
     */
    private double parseRate(String rate) {
        if (rate == null || rate.isBlank()) {
            throw new IllegalArgumentException("利率不能为空");
        }

        // 移除百分号和空格
        String cleaned = rate.replace("%", "").replace("％", "")
                              .replace("百分之", "").trim();

        // 处理中文表述
        if (cleaned.equals("五")) {
            return 0.05;
        } else if (cleaned.equals("三")) {
            return 0.03;
        } else if (cleaned.equals("四")) {
            return 0.04;
        }

        // 数字形式
        double value = Double.parseDouble(cleaned);

        // 如果值大于1，认为是百分比形式，需要除以100
        return value > 1 ? value / 100 : value;
    }

    /**
     * 解析期限
     * 支持格式：10, 10年, 10年期
     */
    private int parseTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("期限不能为空");
        }

        // 移除"年"、"期限"等词语
        String cleaned = term.replace("年", "").replace("期限", "")
                              .replace("期", "").trim();

        return Integer.parseInt(cleaned);
    }

    /**
     * 解析中文数字（简单实现）
     */
    private double parseChineseNumber(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        // 简单的中文数字映射
        text = text.replace("十", "10");
        text = text.replace("百", "100");
        text = text.replace("千", "1000");
        text = text.replace("万", "10000");

        // 如果是纯中文数字，尝试转换
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            // 可以添加更复杂的中文数字解析
            return 0;
        }
    }

    /**
     * 构建错误信息
     */
    private String buildError(String error, String hint) {
        return "❌ " + error + "\n\n" + hint;
    }

    /**
     * 构建摊销计划结果
     */
    private String buildAmortizationResult(
            double principal, double annualRate, int years, int frequency,
            double monthlyRate, int totalPayments, double monthlyPayment,
            FinancialCalculationService.AmortizationSchedule[] schedule) {

        StringBuilder result = new StringBuilder();

        result.append("您提到的是本金").append(String.format("%,.0f", principal))
              .append("万元、年利率").append(String.format("%.0f", annualRate * 100))
              .append("%、期限").append(years).append("年的贷款，要求计算每月还款金额。\n\n");
        result.append("这种情形通常适用于等额本息还款方式（即每月还款金额固定），这是房贷中最常见的还款方式。\n\n");

        // 一、公式说明
        result.append("### 一、等额本息还款公式\n\n");
        result.append("每月还款额 **M** 的计算公式为：\n\n");
        result.append("$$\n");
        result.append("M = P \\times \\frac{r(1 + r)^n}{(1 + r)^n - 1}\n");
        result.append("$$\n\n");
        result.append("**其中：**\n\n");
        result.append("- $P$ ：贷款本金（").append(String.format("%,.0f", principal)).append(" 元）\n");
        result.append("- $r$ ：月利率（年利率 ÷ ").append(frequency).append("）\n");
        result.append("- $n$ ：还款总期数（年数 × ").append(frequency).append("）\n\n");

        // 二、代入数值
        result.append("### 二、代入数值\n\n");
        result.append("年利率 = ").append(String.format("%.0f", annualRate * 100))
              .append("% → 月利率 $r$ = $\\frac{").append(String.format("%.0f", annualRate * 100))
              .append("\\%}{").append(frequency).append("} = ")
              .append(String.format("%.6f", monthlyRate * 100)).append("$\n");
        result.append("贷款期限 = ").append(years).append(" 年 → 总期数 $n$ = ")
              .append(years).append(" × ").append(frequency).append(" = ").append(totalPayments).append("\n");
        result.append("本金 $P$ = ").append(String.format("%,.0f", principal)).append("\n\n");

        result.append("代入公式：\n\n");
        result.append("$$\n");
        result.append("M = ").append(String.format("%,.0f", principal))
              .append(" \\times \\frac{").append(String.format("%.6f", monthlyRate))
              .append("(1 + ").append(String.format("%.6f", monthlyRate))
              .append(")^{").append(totalPayments).append("}}{(1 + ")
              .append(String.format("%.6f", monthlyRate)).append(")^{")
              .append(totalPayments).append("} - 1}\n");
        result.append("$$\n\n");

        // 三、逐步计算
        result.append("### 三、逐步计算\n\n");
        result.append("先计算 $(1 + ").append(String.format("%.6f", monthlyRate))
              .append(")^{").append(totalPayments).append("}$：\n\n");

        double baseFactor = Math.pow(1 + monthlyRate, totalPayments);
        result.append("$$\n");
        result.append("(1 + ").append(String.format("%.6f", monthlyRate)).append(")^{")
              .append(totalPayments).append("} \\approx ")
              .append(String.format("%.5f", baseFactor)).append("\n");
        result.append("$$\n\n");

        result.append("然后代入：\n\n");
        result.append("$$\n");
        result.append("M = ").append(String.format("%,.0f", principal))
              .append(" \\times \\frac{").append(String.format("%.6f", monthlyRate))
              .append(" \\times ").append(String.format("%.5f", baseFactor))
              .append("}{").append(String.format("%.5f", baseFactor))
              .append(" - 1}\n");
        result.append("$$\n\n");

        double numerator = monthlyRate * baseFactor;
        double denominator = baseFactor - 1;
        double multiplier = numerator / denominator;

        result.append("$$\n");
        result.append("= ").append(String.format("%,.0f", principal))
              .append(" \\times \\frac{").append(String.format("%.6f", numerator))
              .append("}{").append(String.format("%.5f", denominator))
              .append("}\n");
        result.append("$$\n\n");

        result.append("$$\n");
        result.append("= ").append(String.format("%,.0f", principal))
              .append(" \\times ").append(String.format("%.6f", multiplier))
              .append("\n");
        result.append("$$\n\n");

        result.append("$$\n");
        result.append("\\approx ").append(String.format("%,.2f", monthlyPayment)).append("\n");
        result.append("$$\n\n");

        // 四、结果
        result.append("### 四、结果\n\n");
        result.append("**每月还款金额约为：¥").append(String.format("%,.2f", monthlyPayment))
              .append(" 元**\n\n");

        // 五、补充信息
        result.append("### 五、补充信息\n\n");
        double totalPayment = monthlyPayment * totalPayments;
        double totalInterest = totalPayment - principal;

        result.append("- 总还款额：").append(String.format("%,.2f", monthlyPayment))
              .append(" × ").append(totalPayments)
              .append(" ≈ ¥").append(String.format("%,.2f", totalPayment)).append("\n");
        result.append("- 总利息：≈ ¥").append(String.format("%,.2f", totalInterest)).append("\n\n");

        result.append("### 六、还款明细（前12期和最后6期）\n");
        result.append("| 期数 | 还款额 | 本金 | 利息 | 剩余本金 |\n");
        result.append("|:----:|:------:|:-----:|:-----:|:--------:|\n");

        for (FinancialCalculationService.AmortizationSchedule s : schedule) {
            if (s.period() <= 12 || s.period() > schedule.length - 6) {
                if (s.period() > 12 && s.period() == schedule.length - 5) {
                    result.append("| ... | ... | ... | ... | ... |\n");
                }
                result.append(String.format("| %d | %,.2f | %,.2f | %,.2f | %,.2f |\n",
                        s.period(), s.payment(), s.principal(), s.interest(), s.balance()));
            }
        }

        result.append("\n### 💡 说明\n");
        result.append("- 等额本息：每期还款金额固定，包含本金和利息\n");
        result.append("- 初期利息占比大，后期本金占比大\n");
        result.append(String.format("- 总利息支出: **%,.2f 元**，占总还款的 **%.1f%%**\n",
                totalInterest, (totalInterest / totalPayment) * 100));

        return result.toString();
    }

    // ========== 旧的参数解析方法（保留给其他工具使用） ==========

    /**
     * 解析债券参数
     */
    private BondParams parseBondParams(String params) {
        double faceValue = extractNumber(params, "面值|本金") == 0 ? 1000 : extractNumber(params, "面值|本金");
        double couponRate = extractPercent(params, "票面|利率|息票");
        double yield = extractPercent(params, "收益率|YTM|到期");
        double years = extractNumber(params, "年|期限|到期");
        int frequency = extractInt(params, "频率|次") == 0 ? 2 : extractInt(params, "频率|次");

        return new BondParams(faceValue, couponRate, yield, years, frequency);
    }

    /**
     * 解析期权参数
     */
    private OptionParams parseOptionParams(String params) {
        double spotPrice = extractNumber(params, "标的价格|标的|现货");
        double strikePrice = extractNumber(params, "行权价|敲定价|执行价");
        double timeToMaturity = extractNumber(params, "到期|时间|期限") / 12.0;
        double riskFreeRate = extractPercent(params, "无风险|利率") / 100;
        double volatility = extractPercent(params, "波动") / 100;
        boolean isCall = params.contains("看涨") || params.toLowerCase().contains("call") ||
                        !params.contains("看跌") && !params.toLowerCase().contains("put");

        return new OptionParams(spotPrice, strikePrice, timeToMaturity, riskFreeRate, volatility, isCall);
    }

    /**
     * 从文本中提取数字
     * 支持两种格式：关键词100000 或 100000关键词
     * 支持中文单位：万（×10000）、千（×1000）
     */
    private double extractNumber(String text, String keywords) {
        String[] keywordArray = keywords.split("\\|");
        for (String keyword : keywordArray) {
            // 格式1：关键词在前，如 "本金100000" 或 "本金100万"
            java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+\\.?[0-9]*)(万|千)?"
            );
            java.util.regex.Matcher matcher1 = pattern1.matcher(text);
            if (matcher1.find()) {
                double value = Double.parseDouble(matcher1.group(1));
                String unit = matcher1.group(2);
                if ("万".equals(unit)) {
                    value *= 10000;
                } else if ("千".equals(unit)) {
                    value *= 1000;
                }
                return value;
            }

            // 格式2：关键词在后，如 "100000元" 或 "100万贷款"
            java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile(
                    "([0-9]+\\.?[0-9]*)(万|千)?\\s*[^0-9.]*" + keyword
            );
            java.util.regex.Matcher matcher2 = pattern2.matcher(text);
            if (matcher2.find()) {
                double value = Double.parseDouble(matcher2.group(1));
                String unit = matcher2.group(2);
                if ("万".equals(unit)) {
                    value *= 10000;
                } else if ("千".equals(unit)) {
                    value *= 1000;
                }
                return value;
            }
        }
        return 0;
    }

    /**
     * 从文本中提取百分比
     * 支持两种格式：关键词5% 或 5%关键词
     */
    private double extractPercent(String text, String keywords) {
        String[] keywordArray = keywords.split("\\|");
        for (String keyword : keywordArray) {
            // 格式1：关键词在前，如 "利率5%" 或 "利率5"
            java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+\\.?[0-9]*)%"
            );
            java.util.regex.Matcher matcher1 = pattern1.matcher(text);
            if (matcher1.find()) {
                return Double.parseDouble(matcher1.group(1)) / 100;
            }

            java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+\\.?[0-9]*)"
            );
            java.util.regex.Matcher matcher2 = pattern2.matcher(text);
            if (matcher2.find()) {
                double value = Double.parseDouble(matcher2.group(1));
                return value > 1 ? value / 100 : value;
            }

            // 格式2：关键词在后，如 "5%利率" 或 "5利率"
            java.util.regex.Pattern pattern3 = java.util.regex.Pattern.compile(
                    "([0-9]+\\.?[0-9]*)%\\s*[^0-9.]*" + keyword
            );
            java.util.regex.Matcher matcher3 = pattern3.matcher(text);
            if (matcher3.find()) {
                return Double.parseDouble(matcher3.group(1)) / 100;
            }

            java.util.regex.Pattern pattern4 = java.util.regex.Pattern.compile(
                    "([0-9]+\\.?[0-9]*)\\s*[^0-9.]*" + keyword
            );
            java.util.regex.Matcher matcher4 = pattern4.matcher(text);
            if (matcher4.find()) {
                double value = Double.parseDouble(matcher4.group(1));
                return value > 1 ? value / 100 : value;
            }
        }
        return 0;
    }

    /**
     * 从文本中提取整数
     * 支持两种格式：关键词10 或 10关键词
     */
    private int extractInt(String text, String keywords) {
        String[] keywordArray = keywords.split("\\|");
        for (String keyword : keywordArray) {
            // 格式1：关键词在前，如 "期限10年"
            java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+)"
            );
            java.util.regex.Matcher matcher1 = pattern1.matcher(text);
            if (matcher1.find()) {
                return Integer.parseInt(matcher1.group(1));
            }

            // 格式2：关键词在后，如 "10年期"
            java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile(
                    "([0-9]+)\\s*" + keyword
            );
            java.util.regex.Matcher matcher2 = pattern2.matcher(text);
            if (matcher2.find()) {
                return Integer.parseInt(matcher2.group(1));
            }
        }
        return 0;
    }

    /**
     * 债券参数
     */
    private record BondParams(
            double faceValue,
            double couponRate,
            double yield,
            double yearsToMaturity,
            int frequency
    ) {}

    /**
     * 期权参数
     */
    private record OptionParams(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility,
            boolean isCall
    ) {}
}
