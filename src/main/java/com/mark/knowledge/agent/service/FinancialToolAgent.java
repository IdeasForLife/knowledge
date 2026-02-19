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
     */
    @Tool("计算投资内部收益率IRR。输入格式: 现金流数组,逗号分隔。例如: -10000,2500,2500,2500,2500,2500 (第一个是初始投资)")
    public String calculateIRR(String cashFlowStr) {
        log.info("LLM 调用 IRR 计算工具: {}", cashFlowStr);

        try {
            String[] parts = cashFlowStr.split(",");
            double[] cashFlows = new double[parts.length];

            for (int i = 0; i < parts.length; i++) {
                cashFlows[i] = Double.parseDouble(parts[i].trim());
            }

            Double irr = financialCalcService.calculateIRR(cashFlows);

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
            result.append("$$\n");
            result.append("\\sum_{t=0}^{n} \\frac{CF_t}{(1+IRR)^t} = 0\n");
            result.append("$$\n\n");

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

        } catch (Exception e) {
            log.error("IRR 计算失败", e);
            return "❌ IRR 计算失败: " + e.getMessage() +
                    "\n\n提示：请确保现金流格式正确，例如：-1000,200,200,200,200";
        }
    }

    /**
     * 计算债券价格
     */
    @Tool("计算债券价格。参数: 面值XX元, 票面利率XX%, 到期收益率XX%, 期限XX年。例如: 面值1000元, 票面5%, YTM4%, 5年期")
    public String calculateBondPrice(String params) {
        log.info("LLM 调用债券价格计算: {}", params);

        try {
            BondParams bp = parseBondParams(params);

            double price = bondCalcService.calculateBondPrice(
                    bp.faceValue,
                    bp.couponRate,
                    bp.yield,
                    bp.yearsToMaturity,
                    bp.frequency
            );

            double pricePercent = (price / bp.faceValue) * 100;
            String status = price > bp.faceValue ? "溢价交易（价格 > 面值）" :
                           price < bp.faceValue ? "折价交易（价格 < 面值）" :
                           "平价交易（价格 = 面值）";

            StringBuilder result = new StringBuilder();

            result.append("## 💰 债券价格计算\n\n");

            result.append("### 债券参数\n");
            result.append(String.format("- 面值: **%,.2f 元**\n", bp.faceValue));
            result.append(String.format("- 票面利率: **%.2f%%**\n", bp.couponRate * 100));
            result.append(String.format("- 到期收益率 (YTM): **%.2f%%**\n", bp.yield * 100));
            result.append(String.format("- 到期年限: **%.1f 年**\n", bp.yearsToMaturity));
            result.append(String.format("- 付息频率: 每年 **%d** 次\n\n", bp.frequency));

            result.append("### 定价公式\n");
            result.append("$$\n");
            result.append("P = \\sum_{t=1}^{n \\times m} \\frac{C/m}{(1+y/m)^t} + \\frac{F}{(1+y/m)^{n \\times m}}\n");
            result.append("$$\n\n");
            result.append("其中：\n");
            result.append("- $P$ = 债券价格\n");
            result.append("- $C$ = 年票息\n");
            result.append("- $F$ = 面值\n");
            result.append("- $y$ = 年到期收益率\n");
            result.append("- $m$ = 每年付息次数\n");
            result.append(String.format("- $n$ = 年限 = %.1f 年\n\n", bp.yearsToMaturity));

            result.append("### 计算结果\n");
            result.append(String.format("- **债券价格**: %,.2f 元\n", price));
            result.append(String.format("- **价格百分比**: %.2f%%\n", pricePercent));
            result.append(String.format("- **状态**: %s\n\n", status));

            result.append("### 💡 说明\n");
            if (price > bp.faceValue) {
                result.append("- 债券溢价交易，因为 **票面利率 > 到期收益率**\n");
            } else if (price < bp.faceValue) {
                result.append("- 债券折价交易，因为 **票面利率 < 到期收益率**\n");
            } else {
                result.append("- 债券平价交易，因为 **票面利率 = 到期收益率**\n");
            }

            return result.toString();

        } catch (Exception e) {
            log.error("债券价格计算失败", e);
            return "❌ 债券价格计算失败: " + e.getMessage() +
                    "\n\n参数格式示例：面值1000元，票面利率5%，到期收益率4%，期限5年，每年付息";
        }
    }

    /**
     * 计算期权价格 (Black-Scholes)
     */
    @Tool("计算期权价格(Black-Scholes模型)。参数: 标的XX元, 行权价XX元, 期限XX年, 利率XX%, 波动率XX%。例如: 标的100元, 行权105元, 1年, 利率3%, 波动25%")
    public String calculateOptionPrice(String params) {
        log.info("LLM 调用期权价格计算: {}", params);

        try {
            OptionParams op = parseOptionParams(params);

            double price = optionCalcService.calculateOptionPrice(
                    op.spotPrice,
                    op.strikePrice,
                    op.timeToMaturity,
                    op.riskFreeRate,
                    op.volatility,
                    op.isCall
            );

            double delta = optionCalcService.calculateDelta(op.spotPrice, op.strikePrice,
                    op.timeToMaturity, op.riskFreeRate, op.volatility, op.isCall);
            double gamma = optionCalcService.calculateGamma(op.spotPrice, op.strikePrice,
                    op.timeToMaturity, op.riskFreeRate, op.volatility);
            double vega = optionCalcService.calculateVega(op.spotPrice, op.strikePrice,
                    op.timeToMaturity, op.riskFreeRate, op.volatility);
            double theta = optionCalcService.calculateTheta(op.spotPrice, op.strikePrice,
                    op.timeToMaturity, op.riskFreeRate, op.volatility, op.isCall);
            double rho = optionCalcService.calculateRho(op.spotPrice, op.strikePrice,
                    op.timeToMaturity, op.riskFreeRate, op.volatility, op.isCall);

            String optionType = op.isCall ? "看涨期权 (Call)" : "看跌期权 (Put)";

            StringBuilder result = new StringBuilder();

            result.append("## 📈 Black-Scholes 期权定价\n\n");

            result.append("### 期权参数\n");
            result.append("- 期权类型: **" + optionType + "**\n");
            result.append(String.format("- 标的资产价格 ($S$): **%,.2f 元**\n", op.spotPrice));
            result.append(String.format("- 行权价格 ($K$): **%,.2f 元**\n", op.strikePrice));
            result.append(String.format("- 到期时间 ($T$): **%.2f 年**\n", op.timeToMaturity));
            result.append(String.format("- 无风险利率 ($r$): **%.2f%%**\n", op.riskFreeRate * 100));
            result.append(String.format("- 波动率 ($\\sigma$): **%.2f%%**\n\n", op.volatility * 100));

            result.append("### Black-Scholes 公式\n");
            result.append("看涨期权：\n");
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

            result.append("### 期权价格\n");
            result.append(String.format("- **期权价格**: %,.4f 元\n\n", price));

            result.append("### Greeks 风险指标\n");
            result.append(String.format("- **Delta ($\\Delta$)**: %.4f %s\n", delta,
                Math.abs(delta) > 0.7 ? "(深度实值)" : Math.abs(delta) < 0.3 ? "(深度虚值)" : "(平值附近)"));
            result.append(String.format("- **Gamma ($\\Gamma$)**: %.4f - Delta 对标的价格敏感度\n", gamma));
            result.append(String.format("- **Vega ($\\nu$)**: %.4f - 波动率变动1%%，期权价格变动 %.4f 元\n", vega, vega));
            result.append(String.format("- **Theta ($\\Theta$)**: %.6f - 时间流逝1天，期权价格变动 %.6f 元\n", theta, theta));
            result.append(String.format("- **Rho ($\\rho$)**: %.4f - 利率变动1%%，期权价格变动 %.4f 元\n\n", rho, rho));

            result.append("### 💡 Greeks 解释\n");
            result.append("- **Delta**: 标的价格变动1元，期权价格变动 " + String.format("%.4f 元\n", delta));
            result.append("- **Gamma**: Delta 的敏感度，反映对冲风险\n");
            result.append("- **Vega**: 波动率变动1%，期权价格变动 " + String.format("%.4f 元\n", vega));
            result.append("- **Theta**: 时间流逝1天，期权价格变动 " + String.format("%.6f 元\n", theta));
            result.append("- **Rho**: 利率变动1%，期权价格变动 " + String.format("%.4f 元\n", rho));

            return result.toString();

        } catch (Exception e) {
            log.error("期权价格计算失败", e);
            return "❌ 期权价格计算失败: " + e.getMessage() +
                    "\n\n参数格式示例：标的价格100，行权价105，1年期，无风险利率3%，波动率20%，看涨期权";
        }
    }

    /**
     * 计算债券久期
     */
    @Tool("计算债券久期和凸度。参数: 面值XX元, 票面利率XX%, 到期收益率XX%, 期限XX年。例如: 面值1000元, 票面5%, YTM4%, 5年")
    public String calculateBondDuration(String params) {
        log.info("LLM 调用久期计算: {}", params);

        try {
            BondParams bp = parseBondParams(params);

            // 先计算债券价格
            double price = bondCalcService.calculateBondPrice(
                    bp.faceValue, bp.couponRate, bp.yield,
                    bp.yearsToMaturity, bp.frequency
            );

            // 计算 Macaulay 久期
            double macaulayDuration = bondCalcService.calculateMacaulayDuration(
                    price, bp.faceValue, bp.couponRate, bp.yield,
                    bp.yearsToMaturity, bp.frequency
            );

            // 计算修正久期
            double modifiedDuration = bondCalcService.calculateModifiedDuration(
                    macaulayDuration, bp.yield, bp.frequency
            );

            // 计算凸度
            double convexity = bondCalcService.calculateConvexity(
                    price, bp.faceValue, bp.couponRate, bp.yield,
                    bp.yearsToMaturity, bp.frequency
            );

            StringBuilder result = new StringBuilder();

            result.append("## 📊 债券久期分析\n\n");

            result.append("### 计算结果\n");
            result.append(String.format("- **Macaulay 久期**: %.2f 年\n", macaulayDuration));
            result.append(String.format("- **修正久期**: %.4f\n", modifiedDuration));
            result.append(String.format("- **凸度**: %.4f\n\n", convexity));

            result.append("### 公式说明\n");
            result.append("**Macaulay 久期**:\n");
            result.append("$$\n");
            result.append("D_{mac} = \\frac{\\sum_{t=1}^{n} \\frac{t \\cdot CF_t}{(1+y)^t}}{P}\n");
            result.append("$$\n\n");

            result.append("**修正久期**:\n");
            result.append("$$\n");
            result.append("D_{mod} = \\frac{D_{mac}}{1 + y/m}\n");
            result.append("$$\n\n");

            result.append("**凸度**:\n");
            result.append("$$\n");
            result.append("凸度 = \\frac{1}{P} \\cdot \\frac{\\partial^2 P}{\\partial y^2}\n");
            result.append("$$\n\n");

            result.append("### 💡 解释\n");
            result.append(String.format("- 修正久期 **%.4f** 表示利率变动 **1%%**，债券价格约变动 **%.4f%%**\n",
                    modifiedDuration, modifiedDuration * 100));
            result.append("- 凸度为正说明债券价格随利率下降的幅度大于随利率上升的幅度");

            return result.toString();

        } catch (Exception e) {
            log.error("久期计算失败", e);
            return "❌ 久期计算失败: " + e.getMessage();
        }
    }

    /**
     * 计算投资摊销计划
     */
    @Tool("计算贷款摊销计划。等额本息还款。关键词：本金、利率、期限、年、月、还款、摊销、等额本息")
    public String calculateAmortization(String params) {
        log.info("LLM 调用摊销计划计算: {}", params);

        try {
            // 解析参数：本金、年利率、年限、每年付款次数
            double principal = extractNumber(params, "本金|贷款|金额");
            double annualRate = extractPercent(params, "利率|利息");
            int years = extractInt(params, "年|期限");
            int frequency = extractInt(params, "频率|次") == 0 ? 12 : extractInt(params, "频率|次");

            // 参数验证
            if (principal <= 0) {
                return "❌ 本金必须大于0。\n\n请提供：本金XX元，例如：本金100000元";
            }
            if (annualRate <= 0 || annualRate > 1) {
                return "❌ 利率必须在 0%-100% 之间。\n\n请提供：利率XX%，例如：年利率3% 或 3%";
            }
            if (years <= 0 || years > 50) {
                return "❌ 期限必须在 1-50 年之间。\n\n请提供：期限XX年，例如：30年期";
            }

            FinancialCalculationService.AmortizationSchedule[] schedule =
                    financialCalcService.calculateAmortization(principal, annualRate, years, frequency);

            double monthlyRate = annualRate / frequency;
            int totalPayments = years * frequency;
            double monthlyPayment = schedule[0].payment();

            StringBuilder result = new StringBuilder();

            result.append("## 📊 等额本息摊销计划\n\n");

            result.append("### 基本参数\n");
            result.append(String.format("- 贷款本金: **%,.0f 元**\n", principal));
            result.append(String.format("- 年利率: **%.2f%%**\n", annualRate * 100));
            result.append(String.format("- 贷款期限: **%d 年**\n", years));
            result.append(String.format("- 还款频率: 每年 **%d** 次\n", frequency));
            result.append(String.format("- 还款期数: **%d** 期\n\n", totalPayments));

            result.append("### 计算公式\n");
            result.append("**等额本息月供公式：**\n");
            result.append("$$\n");
            result.append("M = P \\times \\frac{r(1+r)^n}{(1+r)^n - 1}\n");
            result.append("$$\n\n");
            result.append("其中：\n");
            result.append(String.format("- $P$ = 贷款本金 = %,.0f 元\n", principal));
            result.append(String.format("- $r$ = 每期利率 = %.4f%%\n", monthlyRate * 100));
            result.append(String.format("- $n$ = 还款期数 = %d 期\n\n", totalPayments));

            result.append("### 计算结果\n");
            result.append(String.format("- **每期还款**: %,.2f 元\n", monthlyPayment));
            result.append(String.format("- **还款总额**: %,.2f 元\n", monthlyPayment * totalPayments));
            result.append(String.format("- **支付利息**: %,.2f 元\n\n", (monthlyPayment * totalPayments) - principal));

            result.append("### 还款明细（前12期和最后6期）\n");
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
                    (monthlyPayment * totalPayments) - principal,
                    ((monthlyPayment * totalPayments) - principal) / (monthlyPayment * totalPayments) * 100));

            return result.toString();

        } catch (Exception e) {
            log.error("摊销计划计算失败", e);
            return "❌ 摊销计划计算失败: " + e.getMessage() +
                    "\n\n请提供完整参数：\n" +
                    "- 本金：例如 100000元\n" +
                    "- 利率：例如 3% 或 年利率3%\n" +
                    "- 期限：例如 30年\n\n" +
                    "完整示例：本金100000元，年利率3%，30年期";
        }
    }

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
     */
    private double extractNumber(String text, String keywords) {
        String[] keywordArray = keywords.split("\\|");
        for (String keyword : keywordArray) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*[^0-9.]*([0-9]+\\.?[0-9]*)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }
        return 0;
    }

    /**
     * 从文本中提取百分比
     */
    private double extractPercent(String text, String keywords) {
        String[] keywordArray = keywords.split("\\|");
        for (String keyword : keywordArray) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+\\.?[0-9]*)%"
            );
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1)) / 100;
            }
            pattern = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+\\.?[0-9]*)"
            );
            matcher = pattern.matcher(text);
            if (matcher.find()) {
                double value = Double.parseDouble(matcher.group(1));
                return value > 1 ? value / 100 : value;
            }
        }
        return 0;
    }

    /**
     * 从文本中提取整数
     */
    private int extractInt(String text, String keywords) {
        String[] keywordArray = keywords.split("\\|");
        for (String keyword : keywordArray) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    keyword + "[:：]?\\s*([0-9]+)"
            );
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
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
