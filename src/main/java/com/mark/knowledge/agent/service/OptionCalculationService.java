package com.mark.knowledge.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 期权计算服务
 *
 * 使用 Black-Scholes 模型进行期权计算
 *
 * @author mark
 */
@Service
public class OptionCalculationService {

    private static final Logger log = LoggerFactory.getLogger(OptionCalculationService.class);

    /**
     * Black-Scholes 期权定价
     */
    public double calculateOptionPrice(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility,
            boolean isCall) {

        log.info("计算期权价格: 标的价格={}, 行权价={}, 到期={}, 利率={}, 波动率={}, 类型={}",
                spotPrice, strikePrice, timeToMaturity, riskFreeRate, volatility, isCall ? "看涨" : "看跌");

        double price = blackScholes(spotPrice, strikePrice, timeToMaturity, riskFreeRate, volatility, isCall);

        log.info("期权价格: {}", price);

        return price;
    }

    /**
     * Black-Scholes 公式
     */
    private double blackScholes(
            double S,  // 标的价格
            double K,  // 行权价
            double T,  // 到期时间
            double r,  // 无风险利率
            double sigma,  // 波动率
            boolean isCall) {

        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);

        if (isCall) {
            return S * normalCDF(d1) - K * Math.exp(-r * T) * normalCDF(d2);
        } else {
            return K * Math.exp(-r * T) * normalCDF(-d2) - S * normalCDF(-d1);
        }
    }

    /**
     * 标准正态分布 CDF (使用 Abramowitz-Stegan 近似)
     */
    private double normalCDF(double x) {
        double sign = x < 0 ? -1 : 1;
        x = Math.abs(x) / Math.sqrt(2);

        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return 0.5 * (1.0 + sign * y);
    }

    /**
     * 标准 PDF
     */
    private double normalPDF(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }

    /**
     * 计算 Delta
     */
    public double calculateDelta(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility,
            boolean isCall) {

        double d1 = (Math.log(spotPrice / strikePrice) +
                (riskFreeRate + 0.5 * volatility * volatility) * timeToMaturity) /
                (volatility * Math.sqrt(timeToMaturity));

        double delta = isCall ? normalCDF(d1) : normalCDF(d1) - 1;

        log.info("Delta: {}", delta);

        return delta;
    }

    /**
     * 计算 Gamma
     */
    public double calculateGamma(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility) {

        double d1 = (Math.log(spotPrice / strikePrice) +
                (riskFreeRate + 0.5 * volatility * volatility) * timeToMaturity) /
                (volatility * Math.sqrt(timeToMaturity));

        double gamma = normalPDF(d1) / (spotPrice * volatility * Math.sqrt(timeToMaturity));

        log.info("Gamma: {}", gamma);

        return gamma;
    }

    /**
     * 计算 Vega
     */
    public double calculateVega(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility) {

        double d1 = (Math.log(spotPrice / strikePrice) +
                (riskFreeRate + 0.5 * volatility * volatility) * timeToMaturity) /
                (volatility * Math.sqrt(timeToMaturity));

        double vega = spotPrice * normalPDF(d1) * Math.sqrt(timeToMaturity);

        log.info("Vega: {}", vega);

        return vega;
    }

    /**
     * 计算 Theta
     */
    public double calculateTheta(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility,
            boolean isCall) {

        double d1 = (Math.log(spotPrice / strikePrice) +
                (riskFreeRate + 0.5 * volatility * volatility) * timeToMaturity) /
                (volatility * Math.sqrt(timeToMaturity));

        double d2 = d1 - volatility * Math.sqrt(timeToMaturity);

        double theta;
        if (isCall) {
            theta = (-spotPrice * normalPDF(d1) * volatility / (2 * Math.sqrt(timeToMaturity)) -
                    riskFreeRate * strikePrice * Math.exp(-riskFreeRate * timeToMaturity) * normalCDF(d2)) / 365;
        } else {
            theta = (-spotPrice * normalPDF(d1) * volatility / (2 * Math.sqrt(timeToMaturity)) +
                    riskFreeRate * strikePrice * Math.exp(-riskFreeRate * timeToMaturity) * normalCDF(-d2)) / 365;
        }

        log.info("Theta: {}", theta);

        return theta;
    }

    /**
     * 计算 Rho
     */
    public double calculateRho(
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            double volatility,
            boolean isCall) {

        double d1 = (Math.log(spotPrice / strikePrice) +
                (riskFreeRate + 0.5 * volatility * volatility) * timeToMaturity) /
                (volatility * Math.sqrt(timeToMaturity));

        double d2 = d1 - volatility * Math.sqrt(timeToMaturity);

        double rho;
        if (isCall) {
            rho = strikePrice * timeToMaturity * Math.exp(-riskFreeRate * timeToMaturity) * normalCDF(d2) / 100;
        } else {
            rho = -strikePrice * timeToMaturity * Math.exp(-riskFreeRate * timeToMaturity) * normalCDF(-d2) / 100;
        }

        log.info("Rho: {}", rho);

        return rho;
    }

    /**
     * 计算隐含波动率
     */
    public double calculateImpliedVolatility(
            double optionPrice,
            double spotPrice,
            double strikePrice,
            double timeToMaturity,
            double riskFreeRate,
            boolean isCall) {

        log.info("计算隐含波动率");

        double volatility = 0.3;  // 初始猜测 30%
        double tolerance = 1e-8;
        int maxIterations = 100;

        for (int i = 0; i < maxIterations; i++) {
            double calculatedPrice = blackScholes(spotPrice, strikePrice,
                    timeToMaturity, riskFreeRate, volatility, isCall);
            double diff = calculatedPrice - optionPrice;

            if (Math.abs(diff) < tolerance) {
                log.info("隐含波动率: {}%", volatility * 100);
                return volatility;
            }

            double vega = calculateVega(spotPrice, strikePrice, timeToMaturity, riskFreeRate, volatility);

            volatility = volatility - diff / vega;

            if (volatility < 0.001) volatility = 0.001;
            if (volatility > 5.0) volatility = 5.0;
        }

        log.warn("隐含波动率计算未收敛，返回近似值: {}%", volatility * 100);
        return volatility;
    }
}
