package com.mark.knowledge.agent.service;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 金融计算服务
 *
 * 使用 Apache Commons Math 实现 IRR、NPV 等金融计算
 *
 * @author mark
 */
@Service
public class FinancialCalculationService {

    private static final Logger log = LoggerFactory.getLogger(FinancialCalculationService.class);

    /**
     * 计算内部收益率 (IRR) - 使用牛顿迭代法
     */
    public double calculateIRR(double[] cashFlows) {
        log.info("计算 IRR，现金流: {}", java.util.Arrays.toString(cashFlows));

        if (cashFlows == null || cashFlows.length < 2) {
            throw new IllegalArgumentException("现金流至少需要2个数据点");
        }

        try {
            // 牛顿迭代法求解 IRR
            double guess = 0.1;  // 初始猜测 10%
            double tolerance = 1e-10;
            int maxIterations = 1000;

            for (int i = 0; i < maxIterations; i++) {
                double npv = calculateNPV(cashFlows, guess);
                double npvDerivative = calculateNPVDerivative(cashFlows, guess);

                if (Math.abs(npvDerivative) < tolerance) {
                    break;
                }

                double newGuess = guess - npv / npvDerivative;

                if (Math.abs(newGuess - guess) < tolerance) {
                    log.info("IRR 计算结果: {}%", newGuess * 100);
                    return newGuess;
                }

                guess = newGuess;

                // 确保在合理范围内
                if (guess < -0.99) guess = -0.99;
                if (guess > 10.0) guess = 10.0;
            }

            log.warn("IRR 计算未完全收敛，返回近似值: {}%", guess * 100);
            return guess;

        } catch (Exception e) {
            log.error("IRR 计算失败", e);
            throw new RuntimeException("IRR 计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 NPV（净现值）
     */
    public double calculateNPV(double[] cashFlows, double rate) {
        double npv = 0;
        for (int t = 0; t < cashFlows.length; t++) {
            npv += cashFlows[t] / Math.pow(1 + rate, t);
        }
        return npv;
    }

    /**
     * 计算 NPV 的导数（用于牛顿法）
     */
    private double calculateNPVDerivative(double[] cashFlows, double rate) {
        double derivative = 0;
        for (int t = 1; t < cashFlows.length; t++) {
            double df = 1.0 / Math.pow(1 + rate, t);
            derivative += -t * cashFlows[t] * df / (1 + rate);
        }
        return derivative;
    }

    /**
     * 计算摊销计划
     */
    public AmortizationSchedule[] calculateAmortization(
            double principal,
            double annualRate,
            int years,
            int frequency) {

        double periodRate = annualRate / frequency;
        int totalPeriods = years * frequency;

        // 计算每期付款额
        double periodPayment = principal * periodRate / (1 - Math.pow(1 + periodRate, -totalPeriods));

        AmortizationSchedule[] schedule = new AmortizationSchedule[totalPeriods];
        double balance = principal;

        for (int i = 0; i < totalPeriods; i++) {
            double interestPayment = balance * periodRate;
            double principalPayment = periodPayment - interestPayment;
            balance -= principalPayment;

            if (balance < 0) balance = 0;

            schedule[i] = new AmortizationSchedule(
                    i + 1,
                    periodPayment,
                    principalPayment,
                    interestPayment,
                    balance
            );
        }

        return schedule;
    }

    /**
     * 摊销计划记录
     */
    public record AmortizationSchedule(
            int period,
            double payment,
            double principal,
            double interest,
            double balance
    ) {}
}
