package org.hp.hptlib.material;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record LinearFormula(double base, Map<String, Double> coefficients, double min, double max, Rounding rounding) {
    private static final Set<String> RESERVED = Set.of("base", "min", "max", "round");

    public static LinearFormula parse(JsonObject json, String context) {
        Map<String, Double> coefficients = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (RESERVED.contains(entry.getKey())) {
                continue;
            }
            if (!(entry.getValue() instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                throw new JsonParseException(context + ": coefficient '" + entry.getKey() + "' must be a number");
            }
            coefficients.put(entry.getKey(), primitive.getAsDouble());
        }
        String round = GsonHelper.getAsString(json, "round", "none").toUpperCase(Locale.ROOT);
        Rounding rounding;
        try {
            rounding = Rounding.valueOf(round);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException(context + ": unknown round mode '" + round + "', allowed: none, nearest, floor, ceil");
        }
        return new LinearFormula(
                GsonHelper.getAsDouble(json, "base", 0),
                Map.copyOf(coefficients),
                GsonHelper.getAsDouble(json, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(json, "max", Double.POSITIVE_INFINITY),
                rounding);
    }

    public double evaluate(Map<String, Double> properties) {
        double value = this.base;
        for (Map.Entry<String, Double> entry : this.coefficients.entrySet()) {
            value += entry.getValue() * properties.getOrDefault(entry.getKey(), 0d);
        }
        value = this.rounding.apply(value);
        return Math.max(this.min, Math.min(this.max, value));
    }

    public enum Rounding {
        NONE, NEAREST, FLOOR, CEIL;

        public double apply(double value) {
            return switch (this) {
                case NONE -> value;
                case NEAREST -> Math.round(value);
                case FLOOR -> Math.floor(value);
                case CEIL -> Math.ceil(value);
            };
        }
    }
}
