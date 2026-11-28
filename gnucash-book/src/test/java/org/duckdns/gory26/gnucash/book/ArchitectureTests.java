package org.duckdns.gory26.gnucash.book;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * ADR-0003: amounts are exact rationals, so floating point has no place in the book's API or state. ADR-0014: the
 * book is plain JDBC, so a consumer needs no Spring.
 */
@AnalyzeClasses(packages = "org.duckdns.gory26.gnucash.book", importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("unused") // @ArchTest fields are read by ArchUnit through reflection
class ArchitectureTests {

    private static final Set<String> FLOATING_POINT = Set.of(
            double.class.getName(), float.class.getName(), Double.class.getName(), Float.class.getName());

    private static final DescribedPredicate<JavaClass> FLOATING_POINT_TYPE =
            DescribedPredicate.describe("double or float", type -> FLOATING_POINT.contains(type.getName()));

    private static final DescribedPredicate<List<JavaClass>> ANY_FLOATING_POINT =
            DescribedPredicate.describe("a double or float among them", types -> types.stream().anyMatch(FLOATING_POINT_TYPE));

    @ArchTest
    ArchRule noFloatingPointFields = noFields().should().haveRawType(FLOATING_POINT_TYPE);

    @ArchTest
    ArchRule noFloatingPointReturnTypes = noMethods().should().haveRawReturnType(FLOATING_POINT_TYPE);

    @ArchTest
    ArchRule noFloatingPointParameters = noCodeUnits().should().haveRawParameterTypes(ANY_FLOATING_POINT);

    @ArchTest
    ArchRule noSpring = noClasses().should().dependOnClassesThat().resideInAPackage("org.springframework..");
}
