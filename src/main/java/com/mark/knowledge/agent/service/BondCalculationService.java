package com.mark.knowledge.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 债券计算服务
 *
 * 使用标准金融公式计算债券相关指标
 *
 * @author mark
 */
@Service
public class BondCalculationService {

    private static final Logger log = LoggerFactory.getLogger(BondCalculationService.class);

    /**
     * 计算固定利率债券价格
     */
    public double calculateBondPrice(
            double faceValue,
            double couponRate,
            double yield,
            double yearsToMaturity,
            int frequency) {

        log.info("计算债券价格: 面值={}, 票面利率={}, 到期收益率={}, 年限={}, 付息频率={}",
                faceValue, couponRate, yield, yearsToMaturity, frequency);

        double periodicCoupon = faceValue * couponRate / frequency;
        double periodicYield = yield / frequency;
        int totalPeriods = (int) (yearsToMaturity * frequency);

        double price = 0;

        // 计算票息现值
        for (int i = 1; i <= totalPeriods; i++) {
            price += periodicCoupon / Math.pow(1 + periodicYield, i);
        }

        // 计算面值现值
        price += faceValue / Math.pow(1 + periodicYield, totalPeriods);

        log.info("债券价格计算结果: {}", price);

        return price;
    }

    /**
     * 计算债券到期收益率 (YTM) - 使用牛顿迭代法
     */
    public double calculateYTM(
            double price,
            double faceValue,
            double couponRate,
            double yearsToMaturity,
            int frequency) {

        log.info("计算 YTM: 价格={}, 面值={}, 票面利率={}", price, faceValue, couponRate);

        double ytm = couponRate;  // 初始猜测
        double tolerance = 1e-10;
        int maxIterations = 1000;

        for (int i = 0; i < maxIterations; i++) {
            double calculatedPrice = calculateBondPrice(faceValue, couponRate, ytm, yearsToMaturity, frequency);
            double diff = calculatedPrice - price;

            if (Math.abs(diff) < tolerance) {
                log.info("YTM 计算结果: {}%", ytm * 100);
                return ytm;
            }

            // 计算导数（数值微分）
            double delta = 0.0001;
            double priceDelta = calculateBondPrice(faceValue, couponRate, ytm + delta, yearsToMaturity, frequency);
            double derivative = (priceDelta - calculatedPrice) / delta;

            ytm = ytm - diff / derivative;

            if (ytm < -0.99) ytm = -0.99;
            if (ytm > 10.0) ytm = 10.0;
        }

        log.warn("YTM 计算未收敛，返回近似值: {}%", ytm * 100);
        return ytm;
    }

    /**
     * 计算 Macaulay 久期
     */
    public double calculateMacaulayDuration(
            double price,
            double faceValue,
            double couponRate,
            double ytm,
            double yearsToMaturity,
            int frequency) {

        log.info("计算 Macaulay 久期");

        double periodicYield = ytm / frequency;
        double periodicCoupon = faceValue * couponRate / frequency;
        int totalPeriods = (int) (yearsToMaturity * frequency);

        double weightedTime = 0;

        for (int i = 1; i <= totalPeriods; i++) {
            double timeInYears = i / (double) frequency;
            double cashFlow = periodicCoupon;
            if (i == totalPeriods) {
                cashFlow += faceValue;
            }
            double pv = cashFlow / Math.pow(1 + periodicYield, i);
            weightedTime += timeInYears * pv;
        }

        double macaulayDuration = weightedTime / price;

        log.info("Macaulay 久期: {} 年", macaulayDuration);

        return macaulayDuration;
    }

    /**
     * 计算修正久期
     */
    public double calculateModifiedDuration(
            double macaulayDuration,
            double ytm,
            int frequency) {

        double modifiedDuration = macaulayDuration / (1 + ytm / frequency);

        log.info("修正久期: {}", modifiedDuration);

        return modifiedDuration;
    }

    /**
     * 计算凸度
     */
    public double calculateConvexity(
            double price,
            double faceValue,
            double couponRate,
            double ytm,
            double yearsToMaturity,
            int frequency) {

        log.info("计算凸度");

        double periodicYield = ytm / frequency;
        double periodicCoupon = faceValue * couponRate / frequency;
        int totalPeriods = (int) (yearsToMaturity * frequency);

        double convexity = 0;

        for (int i = 1; i <= totalPeriods; i++) {
            double cashFlow = periodicCoupon;
            if (i == totalPeriods) {
                cashFlow += faceValue;
            }
            double pv = cashFlow / Math.pow(1 + periodicYield, i);
            convexity += (i * (i + 1) * pv);
        }

        convexity = convexity / (price * Math.pow(1 + periodicYield, 2) * frequency * frequency);

        log.info("凸度: {}", convexity);

        return convexity;
    }

    /**
     * 零息债券价格
     */
    public double calculateZeroCouponBondPrice(
            double faceValue,
            double yield,
            double yearsToMaturity) {

        double price = faceValue / Math.pow(1 + yield, yearsToMaturity);

        log.info("零息债券价格: {}", price);

        return price;
    }
}
