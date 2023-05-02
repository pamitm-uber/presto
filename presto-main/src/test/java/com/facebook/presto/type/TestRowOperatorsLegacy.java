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
package com.facebook.presto.type;

import com.facebook.presto.Session;
import com.facebook.presto.common.type.StandardTypes;
import com.facebook.presto.operator.scalar.AbstractTestFunctions;
import com.facebook.presto.operator.scalar.FunctionAssertions;
import com.facebook.presto.spi.function.LiteralParameters;
import com.facebook.presto.spi.function.ScalarFunction;
import com.facebook.presto.spi.function.SqlType;
import com.facebook.presto.sql.analyzer.FeaturesConfig;
import io.airlift.slice.Slice;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.facebook.presto.SessionTestUtils.TEST_SESSION;
import static com.facebook.presto.SystemSessionProperties.LEGACY_ROW_FIELD_ORDINAL_ACCESS;
import static com.facebook.presto.common.type.JsonType.JSON;
import static com.facebook.presto.testing.DateTimeTestingUtils.sqlTimestampOf;
import static java.lang.String.format;

public class TestRowOperatorsLegacy
        extends AbstractTestFunctions
{
    public TestRowOperatorsLegacy()
    {
        super(new FeaturesConfig().setLegacyRowToJsonCast(true));
    }

    private static FunctionAssertions legacyRowFieldOrdinalAccess;

    @BeforeClass
    public void setUp()
    {
        registerScalar(getClass());
        legacyRowFieldOrdinalAccess = new FunctionAssertions(
                Session.builder(session)
                        .setSystemProperty(LEGACY_ROW_FIELD_ORDINAL_ACCESS, "true")
                        .build(),
                new FeaturesConfig());
    }

    @AfterClass(alwaysRun = true)
    public final void tearDown()
    {
        legacyRowFieldOrdinalAccess.close();
        legacyRowFieldOrdinalAccess = null;
    }

    @ScalarFunction
    @LiteralParameters("x")
    @SqlType(StandardTypes.JSON)
    public static Slice uncheckedToJson(@SqlType("varchar(x)") Slice slice)
    {
        return slice;
    }

    @Test
    public void testRowToJson()
    {
        assertFunction("cast(cast (null as ROW(BIGINT, VARCHAR)) AS JSON)", JSON, null);
        assertFunction("cast(ROW(null, null) as json)", JSON, "[null,null]");

        assertFunction("cast(ROW(true, false, null) AS JSON)", JSON, "[true,false,null]");

        assertFunction(
                "cast(cast(ROW(12, 12345, 123456789, 1234567890123456789, null, null, null, null) AS ROW(TINYINT, SMALLINT, INTEGER, BIGINT, TINYINT, SMALLINT, INTEGER, BIGINT)) AS JSON)",
                JSON,
                "[12,12345,123456789,1234567890123456789,null,null,null,null]");

        assertFunction(
                "CAST(ROW(CAST(3.14E0 AS REAL), 3.1415E0, 1e308, DECIMAL '3.14', DECIMAL '12345678901234567890.123456789012345678', CAST(null AS REAL), CAST(null AS DOUBLE), CAST(null AS DECIMAL)) AS JSON)",
                JSON,
                "[3.14,3.1415,1.0E308,3.14,12345678901234567890.123456789012345678,null,null,null]");

        assertFunction(
                "CAST(ROW('a', 'bb', CAST(null as VARCHAR), JSON '123', JSON '3.14', JSON 'false', JSON '\"abc\"', JSON '[1, \"a\", null]', JSON '{\"a\": 1, \"b\": \"str\", \"c\": null}', JSON 'null', CAST(null AS JSON)) AS JSON)",
                JSON,
                "[\"a\",\"bb\",null,123,3.14,false,\"abc\",[1,\"a\",null],{\"a\":1,\"b\":\"str\",\"c\":null},null,null]");
        assertFunction(
                "CAST(ROW(DATE '2001-08-22', DATE '2001-08-23', null) AS JSON)",
                JSON,
                "[\"2001-08-22\",\"2001-08-23\",null]");

        assertFunction(
                "CAST(ROW(TIMESTAMP '1970-01-01 00:00:01', cast(null as TIMESTAMP)) AS JSON)",
                JSON,
                format("[\"%s\",null]", sqlTimestampOf(1970, 1, 1, 0, 0, 1, 0, TEST_SESSION)));

        assertFunction(
                "cast(ROW(ARRAY[1, 2], ARRAY[3, null], ARRAY[], ARRAY[null, null], CAST(null AS ARRAY<BIGINT>)) AS JSON)",
                JSON,
                "[[1,2],[3,null],[],[null,null],null]");
        assertFunction(
                "cast(ROW(MAP(ARRAY['b', 'a'], ARRAY[2, 1]), MAP(ARRAY['three', 'none'], ARRAY[3, null]), MAP(), MAP(ARRAY['h2', 'h1'], ARRAY[null, null]), CAST(NULL as MAP<VARCHAR, BIGINT>)) AS JSON)",
                JSON,
                "[{\"a\":1,\"b\":2},{\"none\":null,\"three\":3},{},{\"h1\":null,\"h2\":null},null]");
        assertFunction(
                "cast(ROW(ROW(1, 2), ROW(3, CAST(null as INTEGER)), CAST(ROW(null, null) AS ROW(INTEGER, INTEGER)), null) AS JSON)",
                JSON,
                "[[1,2],[3,null],[null,null],null]");

        // other miscellaneous tests
        assertFunction("CAST(ROW(1, 2) AS JSON)", JSON, "[1,2]");
        assertFunction("CAST(CAST(ROW(1, 2) AS ROW(a BIGINT, b BIGINT)) AS JSON)", JSON, "[1,2]");
        assertFunction("CAST(ROW(1, NULL) AS JSON)", JSON, "[1,null]");
        assertFunction("CAST(ROW(1, CAST(NULL AS INTEGER)) AS JSON)", JSON, "[1,null]");
        assertFunction("CAST(ROW(1, 2.0E0) AS JSON)", JSON, "[1,2.0]");
        assertFunction("CAST(ROW(1.0E0, 2.5E0) AS JSON)", JSON, "[1.0,2.5]");
        assertFunction("CAST(ROW(1.0E0, 'kittens') AS JSON)", JSON, "[1.0,\"kittens\"]");
        assertFunction("CAST(ROW(TRUE, FALSE) AS JSON)", JSON, "[true,false]");
        assertFunction("CAST(ROW(FALSE, ARRAY [1, 2], MAP(ARRAY[1, 3], ARRAY[2.0E0, 4.0E0])) AS JSON)", JSON, "[false,[1,2],{\"1\":2.0,\"3\":4.0}]");
        assertFunction("CAST(row(1.0, 123123123456.6549876543) AS JSON)", JSON, "[1.0,123123123456.6549876543]");
    }
}
