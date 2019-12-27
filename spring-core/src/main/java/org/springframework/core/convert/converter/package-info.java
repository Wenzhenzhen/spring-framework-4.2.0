/**
 * SPI to implement Converters for the type conversion system.
 *
 * 四种不同的转换器承载着不同的转换过程：
 * 1. Converter：用于 1:1 的 source -> target 类型转换
 * 2. ConverterFactory：用于 1:N 的 source -> target 类型转换
 * 3. GenericConverter用于 N:N 的 source -> target 类型转换
 * 4. ConditionalConverter：有条件的 source -> target 类型转换
 */
package org.springframework.core.convert.converter;
