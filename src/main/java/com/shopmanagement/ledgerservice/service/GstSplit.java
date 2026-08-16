package com.shopmanagement.ledgerservice.service;

/**
 * Resolves GST into CGST / SGST / IGST plus leftover on the control ledger.
 * Does not invent a split when the source document only has a tax total.
 */
public final class GstSplit {

    public final double cgst;
    public final double sgst;
    public final double igst;
    public final double residual;
    public final double total;

    private GstSplit(double cgst, double sgst, double igst, double residual) {
        this.cgst = round2(Math.max(0, cgst));
        this.sgst = round2(Math.max(0, sgst));
        this.igst = round2(Math.max(0, igst));
        this.residual = round2(Math.max(0, residual));
        this.total = round2(this.cgst + this.sgst + this.igst + this.residual);
    }

    public GstSplit cappedTo(double maxTotal) {
        double cap = round2(Math.max(0, maxTotal));
        if (total <= cap + 0.009) {
            return this;
        }
        if (total <= 0.009) {
            return new GstSplit(0, 0, 0, cap);
        }
        double ratio = cap / total;
        return new GstSplit(cgst * ratio, sgst * ratio, igst * ratio, residual * ratio);
    }

    public static GstSplit of(Double taxAmount, Double cgst, Double sgst, Double igst) {
        double c = round2(Math.max(0, n(cgst)));
        double s = round2(Math.max(0, n(sgst)));
        double i = round2(Math.max(0, n(igst)));
        double split = round2(c + s + i);
        double tax = round2(Math.max(0, n(taxAmount)));
        if (split <= 0.009) {
            return new GstSplit(0, 0, 0, tax);
        }
        if (tax <= 0.009) {
            return new GstSplit(c, s, i, 0);
        }
        double residual = round2(tax - split);
        if (residual < -0.05) {
            return new GstSplit(c, s, i, 0);
        }
        if (residual < 0) {
            residual = 0;
        }
        return new GstSplit(c, s, i, residual);
    }

    /** Scale a header GST split by returned tax / original tax. */
    public static GstSplit prorate(
            Double sourceTax, Double sourceCgst, Double sourceSgst, Double sourceIgst, double portionTax) {
        double part = round2(Math.max(0, portionTax));
        GstSplit source = of(sourceTax, sourceCgst, sourceSgst, sourceIgst);
        if (source.total <= 0.009 || part <= 0.009) {
            return of(part, 0d, 0d, 0d);
        }
        if (Math.abs(part - source.total) <= 0.05) {
            return source;
        }
        double ratio = part / source.total;
        return of(
                part,
                round2(source.cgst * ratio),
                round2(source.sgst * ratio),
                round2(source.igst * ratio));
    }

    public boolean isZero() {
        return total <= 0.009;
    }

    private static double n(Double value) {
        return value == null ? 0.0 : value;
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
