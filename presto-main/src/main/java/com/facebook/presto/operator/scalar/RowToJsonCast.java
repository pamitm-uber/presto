/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.scalar;

import com.facebook.presto.annotation.UsedByGeneratedCode;
import com.facebook.presto.common.block.Block;
import com.facebook.presto.common.function.OperatorType;
import com.facebook.presto.common.function.SqlFunctionProperties;
import com.facebook.presto.common.type.StandardTypes;
import com.facebook.presto.common.type.Type;
import com.facebook.presto.common.type.TypeSignatureParameter;
import com.facebook.presto.metadata.BoundVariables;
import com.facebook.presto.metadata.FunctionAndTypeManager;
import com.facebook.presto.metadata.SqlOperator;
import com.facebook.presto.util.JsonUtil.JsonGeneratorWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.ImmutableList;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceOutput;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import static com.facebook.presto.common.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.operator.scalar.JsonOperators.JSON_FACTORY;
import static com.facebook.presto.operator.scalar.ScalarFunctionImplementationChoice.ArgumentProperty.valueTypeArgumentProperty;
import static com.facebook.presto.operator.scalar.ScalarFunctionImplementationChoice.NullConvention.RETURN_NULL_ON_NULL;
import static com.facebook.presto.spi.StandardErrorCode.INVALID_CAST_ARGUMENT;
import static com.facebook.presto.spi.function.Signature.withVariadicBound;
import static com.facebook.presto.util.Failures.checkCondition;
import static com.facebook.presto.util.JsonUtil.JsonGeneratorWriter.createJsonGeneratorWriter;
import static com.facebook.presto.util.JsonUtil.canCastToJson;
import static com.facebook.presto.util.JsonUtil.createJsonGenerator;
import static com.facebook.presto.util.Reflection.methodHandle;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;

public class RowToJsonCast
        extends SqlOperator
{
    public static final RowToJsonCast ROW_TO_JSON = new RowToJsonCast(false);
    public static final RowToJsonCast LEGACY_ROW_TO_JSON = new RowToJsonCast(true);

    private static final MethodHandle METHOD_HANDLE = methodHandle(RowToJsonCast.class, "toJsonObject", List.class, List.class, SqlFunctionProperties.class, Block.class);
    private static final MethodHandle LEGACY_METHOD_HANDLE = methodHandle(RowToJsonCast.class, "toJsonArray", List.class, SqlFunctionProperties.class, Block.class);

    private final boolean legacyRowToJson;

    private RowToJsonCast(boolean legacyRowToJson)
    {
        super(OperatorType.CAST,
                ImmutableList.of(withVariadicBound("T", "row")),
                ImmutableList.of(),
                parseTypeSignature(StandardTypes.JSON),
                ImmutableList.of(parseTypeSignature("T")));
        this.legacyRowToJson = legacyRowToJson;
    }

    @Override
    public BuiltInScalarFunctionImplementation specialize(BoundVariables boundVariables, int arity, FunctionAndTypeManager functionAndTypeManager)
    {
        checkArgument(arity == 1, "Expected arity to be 1");
        Type type = boundVariables.getTypeVariable("T");
        checkCondition(canCastToJson(type), INVALID_CAST_ARGUMENT, "Cannot cast %s to JSON", type);

        List<Type> fieldTypes = type.getTypeParameters();
        List<JsonGeneratorWriter> fieldWriters = new ArrayList<>(fieldTypes.size());
        MethodHandle methodHandle;
        if (legacyRowToJson) {
            for (Type fieldType : fieldTypes) {
                fieldWriters.add(createJsonGeneratorWriter(fieldType, true));
            }
            methodHandle = LEGACY_METHOD_HANDLE.bindTo(fieldWriters);
        }
        else {
            List<TypeSignatureParameter> typeSignatureParameters = type.getTypeSignature().getParameters();
            List<String> fieldNames = new ArrayList<>(fieldTypes.size());
            for (int i = 0; i < fieldTypes.size(); i++) {
                fieldNames.add(typeSignatureParameters.get(i).getNamedTypeSignature().getName().orElse(""));
                fieldWriters.add(createJsonGeneratorWriter(fieldTypes.get(i), false));
            }
            methodHandle = METHOD_HANDLE.bindTo(fieldNames).bindTo(fieldWriters);
        }
        return new BuiltInScalarFunctionImplementation(
                false,
                ImmutableList.of(valueTypeArgumentProperty(RETURN_NULL_ON_NULL)),
                methodHandle);
    }

    @UsedByGeneratedCode
    public static Slice toJsonArray(List<JsonGeneratorWriter> fieldWriters, SqlFunctionProperties session, Block block)
    {
        try {
            SliceOutput output = new DynamicSliceOutput(40);
            try (JsonGenerator jsonGenerator = createJsonGenerator(JSON_FACTORY, output)) {
                jsonGenerator.writeStartArray();
                for (int i = 0; i < block.getPositionCount(); i++) {
                    fieldWriters.get(i).writeJsonValue(jsonGenerator, block, i, session);
                }
                jsonGenerator.writeEndArray();
            }
            return output.slice();
        }
        catch (IOException e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @UsedByGeneratedCode
    public static Slice toJsonObject(List<String> fieldNames, List<JsonGeneratorWriter> fieldWriters, SqlFunctionProperties session, Block block)
    {
        try {
            SliceOutput output = new DynamicSliceOutput(40);
            try (JsonGenerator jsonGenerator = createJsonGenerator(JSON_FACTORY, output)) {
                jsonGenerator.writeStartObject();
                for (int i = 0; i < block.getPositionCount(); i++) {
                    jsonGenerator.writeFieldName(fieldNames.get(i));
                    fieldWriters.get(i).writeJsonValue(jsonGenerator, block, i, session);
                }
                jsonGenerator.writeEndObject();
            }
            return output.slice();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
