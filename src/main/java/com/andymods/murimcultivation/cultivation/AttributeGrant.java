package com.andymods.murimcultivation.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One attribute bonus a realm confers, e.g. "+6 max health" or "+15% movement speed".
 * Declared in the realm's JSON so balance is a datapack edit, not a recompile.
 */
public record AttributeGrant(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {

    /**
     * Operations by their lowercase name — {@code add_value}, {@code add_multiplied_base},
     * {@code add_multiplied_total}. Derived from the enum rather than hardcoded so a
     * Minecraft version that adds an operation picks it up for free.
     */
    private static final Map<String, AttributeModifier.Operation> OPERATIONS_BY_NAME =
            Arrays.stream(AttributeModifier.Operation.values())
                    .collect(Collectors.toUnmodifiableMap(
                            op -> op.name().toLowerCase(Locale.ROOT), Function.identity()));

    public static final Codec<AttributeModifier.Operation> OPERATION_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                AttributeModifier.Operation operation = OPERATIONS_BY_NAME.get(name.toLowerCase(Locale.ROOT));
                return operation == null
                        ? DataResult.error(() -> "Unknown attribute modifier operation '" + name
                        + "'; expected one of " + OPERATIONS_BY_NAME.keySet())
                        : DataResult.success(operation);
            },
            operation -> operation.name().toLowerCase(Locale.ROOT));

    public static final Codec<AttributeGrant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(AttributeGrant::attribute),
            Codec.DOUBLE.fieldOf("amount").forGetter(AttributeGrant::amount),
            OPERATION_CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE)
                    .forGetter(AttributeGrant::operation)
    ).apply(instance, AttributeGrant::new));
}
