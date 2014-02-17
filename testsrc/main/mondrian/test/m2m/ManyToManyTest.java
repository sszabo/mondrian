/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2014 Pentaho
// All Rights Reserved.
*/
package mondrian.test.m2m;

import mondrian.calc.TupleList;
import mondrian.calc.impl.ListTupleList;
import mondrian.olap.Annotation;
import mondrian.olap.Dimension;
import mondrian.olap.Exp;
import mondrian.olap.Hierarchy;
import mondrian.olap.Id.Segment;
import mondrian.olap.Level;
import mondrian.olap.MatchType;
import mondrian.olap.Member;
import mondrian.olap.MondrianProperties;
import mondrian.olap.OlapElement;
import mondrian.olap.Property;
import mondrian.olap.SchemaReader;
import mondrian.rolap.DataSourceChangeListenerTest;
import mondrian.rolap.ManyToManyUtil;
import mondrian.rolap.RolapUtil;
import mondrian.test.TestContext;
import mondrian.test.loader.CsvDBTestCase;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TODO: Test scenarios where multiple many to many dimensions are at play
 *       within the slicer
 * TODO: Test Virtual Cube scenario with same dimension but two different m2m
 *       relationships
 * TODO: Test with more than two bridge dimensions
 *
 * Potential future work for Many to Many Dimensions:
 *  - Add tests with snow flake (multi-table, "join") dimensions
 *  - Add tests and implement nested Many to Many dimensions
 *  - Support M2M AggLevel agg tables.  Today, you can only use agg tables who
 *    link via foreign key to a many to many dimension.
 *
 * @author Will Gorman (wgorman@pentaho.com)
 *
 */
public class ManyToManyTest  extends CsvDBTestCase {

    private static final String DIRECTORY = "testsrc/main/mondrian/test/m2m";

    private static final String FILENAME = "many_to_many.csv";

    @Override
    protected String getDirectoryName() {
        return DIRECTORY;
    }

    @Override
    protected String getFileName() {
        return FILENAME;
    }

    @Override
    protected TestContext createTestContext() {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Account\">\n"
            + "    <Hierarchy name=\"Account\" primaryKey=\"id_account\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_account\"/>\n"
            + "      <Level name=\"Account\" uniqueMembers=\"true\" column=\"id_account\"  type=\"Integer\" nameColumn=\"nm_account\" approxRowCount=\"6\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Customer\">\n"
            + "    <Hierarchy name=\"Customer\" primaryKey=\"id_customer\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_customer\"/>\n"
            + "      <Level name=\"Customer Name\" uniqueMembers=\"true\" column=\"id_customer\" type=\"Integer\" nameColumn=\"nm_customer\" approxRowCount=\"4\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Date\">\n"
            + "    <Hierarchy name=\"Date\" primaryKey=\"ID_Date\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_date\"/>\n"
            + "      <Level name=\"Date\" uniqueMembers=\"true\" column=\"id_date\"  type=\"Integer\" nameColumn=\"nm_date\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"CustomerAccountBridge\" visible=\"false\">\n"
            + "    <Table name=\"m2m_bridge_accountcustomer\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_customer\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"id_customer\"/>\n"
            + "  </Cube>\n"
            + "\n"
            + "  <Cube name=\"M2M\">\n"
            + "    <Table name=\"m2m_fact_balance\">\n"
            + "      <AggName name=\"m2m_fact_balance_date_agg\">\n"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggForeignKey factColumn=\"id_account\" aggColumn=\"id_account\" />\n"
            + "        <AggMeasure name=\"[Measures].[Amount]\" column=\"amount_sum\"/>\n"
            + "        <AggMeasure name=\"[Measures].[Count]\" column=\"amount_count\" />\n"
            + "      </AggName>\n"
            + "    </Table>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_account\" bridgeCube=\"CustomerAccountBridge\"/>\n"
            + "    <DimensionUsage name=\"Date\" source=\"Date\" foreignKey=\"id_date\"/>\n"
            + "    <Measure name=\"Amount\" aggregator=\"sum\" column=\"Amount\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"Amount\"/>\n"
            + "    <Measure name=\"Distinct Account Count\" aggregator=\"distinct-count\" column=\"id_account\"/>\n"
            + "  </Cube>\n"
            + "\n"
            + "  <Cube name=\"M2MCount\">\n"
            + "    <Table name=\"m2m_fact_count\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account_diff\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_account_diff\" bridgeCube=\"CustomerAccountBridge\"/>\n"
            + "    <Measure name=\"Total Count\" aggregator=\"count\" column=\"total\"/>\n"
            + "  </Cube>"
            + "\n"
            + "  <VirtualCube name=\"M2MVirtual\" defaultMeasure=\"Amount\">\n"
            + "    <CubeUsages>\n"
            + "      <CubeUsage cubeName=\"M2M\" ignoreUnrelatedDimensions=\"true\"/>\n"
            + "      <CubeUsage cubeName=\"M2MCount\" ignoreUnrelatedDimensions=\"true\"/>\n"
            + "    </CubeUsages>\n"
            + "    <VirtualCubeDimension name=\"Account\"/>\n"
            + "    <VirtualCubeDimension name=\"Customer\"/>\n"
            + "    <VirtualCubeDimension name=\"Date\"/>\n"
            + "    <VirtualCubeMeasure cubeName=\"M2M\" name=\"[Measures].[Amount]\"/>\n"
            + "    <VirtualCubeMeasure cubeName=\"M2MCount\" name=\"[Measures].[Total Count]\"/>\n"
            + "  </VirtualCube>"
            + "</Schema>\n");
        return testContext;
    }

    protected TestContext createMultiLevelTestContext(boolean withAggTbl) {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Account\">\n"
            + "    <Hierarchy name=\"Account\" primaryKey=\"id_account\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_account\"/>\n"
            + "      <Level name=\"AcctType\" uniqueMembers=\"true\" column=\"acct_type\" approxRowCount=\"2\"/>\n"
            + "      <Level name=\"Account\" uniqueMembers=\"true\" column=\"id_account\"  type=\"Integer\" nameColumn=\"nm_account\" approxRowCount=\"6\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Customer\">\n"
            + "    <Hierarchy name=\"Customer\" primaryKey=\"id_customer\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_customer\"/>\n"
            + "      <Level name=\"Location\" uniqueMembers=\"true\" column=\"loc\" approxRowCount=\"2\"/>\n"
            + "      <Level name=\"Customer Name\" uniqueMembers=\"true\" column=\"id_customer\"  type=\"Integer\" nameColumn=\"nm_customer\" approxRowCount=\"4\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Date\">\n"
            + "    <Hierarchy name=\"Date\" primaryKey=\"ID_Date\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_date\"/>\n"
            + "      <Level name=\"Date\" uniqueMembers=\"true\" column=\"id_date\"  type=\"Integer\" nameColumn=\"nm_date\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"CustomerAccountBridge\" visible=\"false\">\n"
            + "    <Table name=\"m2m_bridge_accountcustomer\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_customer\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"id_customer\"/>\n"
            + "  </Cube>"
            + "\n"
            + "  <Cube name=\"M2M\">\n"
            + "    <Table name=\"m2m_fact_balance\">\n"
            + (withAggTbl ? 
              "      <AggName name=\"m2m_fact_balance_mlvl_agg\">\n"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggForeignKey factColumn=\"id_date\" aggColumn=\"id_date\" />\n"
            + "        <AggMeasure name=\"[Measures].[Amount]\" column=\"amount_sum\"/>\n"
            + "        <AggMeasure name=\"[Measures].[Count]\" column=\"amount_count\" />\n"
            + "        <AggLevel name=\"[Account].[AcctType]\" column=\"acct_type\"/>\n"
            + "        <AggLevel name=\"[Customer].[Location]\" column=\"cust_loc\"/>\n"
            + "      </AggName>\n"
              : "" )
            + "    </Table>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_account\" bridgeCube=\"CustomerAccountBridge\"/>\n"
            + "    <DimensionUsage name=\"Date\" source=\"Date\" foreignKey=\"id_date\"/>\n"
            + "    <Measure name=\"Amount\" aggregator=\"sum\" column=\"Amount\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"Amount\"/>\n"
            + "  </Cube>\n"
            + "  <Role name=\"role_test_1\">\n"
            + "    <SchemaGrant access=\"none\">\n"
            + "      <CubeGrant cube=\"M2M\" access=\"all\">\n"
            + "        <DimensionGrant dimension=\"[Account]\" access=\"none\"/>\n"
            + "      </CubeGrant>\n"
            + "    </SchemaGrant>\n"
            + "  </Role>\n"
            + "  <Role name=\"role_test_2\">\n"
            + "    <SchemaGrant access=\"none\">\n"
            + "      <CubeGrant cube=\"M2M\" access=\"all\">\n"
            + "        <HierarchyGrant hierarchy=\"[Customer]\" access=\"custom\" rollupPolicy=\"full\">\n"
            + "          <MemberGrant member=\"[Customer].[San Francisco]\" access=\"all\"/>\n"
            + "          <MemberGrant member=\"[Customer].[San Francisco].[Mark]\" access=\"none\"/>\n"
            + "        </HierarchyGrant>\n"
            + "      </CubeGrant>\n"
            + "    </SchemaGrant>\n"
            + "  </Role>\n"
            + "  <Role name=\"role_test_3\">\n"
            + "    <SchemaGrant access=\"none\">\n"
            + "      <CubeGrant cube=\"M2M\" access=\"all\">\n"
            + "        <HierarchyGrant hierarchy=\"[Customer]\" access=\"custom\" rollupPolicy=\"partial\">\n"
            + "          <MemberGrant member=\"[Customer].[San Francisco]\" access=\"all\"/>\n"
            + "          <MemberGrant member=\"[Customer].[San Francisco].[Mark]\" access=\"none\"/>\n"
            + "        </HierarchyGrant>\n"
            + "      </CubeGrant>\n"
            + "    </SchemaGrant>\n"
            + "  </Role>\n"
            + "  <Role name=\"role_test_4\">\n"
            + "    <SchemaGrant access=\"none\">\n"
            + "      <CubeGrant cube=\"M2M\" access=\"all\">\n"
            + "        <HierarchyGrant hierarchy=\"[Customer]\" access=\"custom\" rollupPolicy=\"hidden\">\n"
            + "          <MemberGrant member=\"[Customer].[San Francisco]\" access=\"all\"/>\n"
            + "          <MemberGrant member=\"[Customer].[San Francisco].[Mark]\" access=\"none\"/>\n"
            + "        </HierarchyGrant>\n"
            + "      </CubeGrant>\n"
            + "    </SchemaGrant>\n"
            + "  </Role>\n"
            + "</Schema>\n");
        return testContext;
    }

    protected TestContext createMultiHierarchyTestContext() {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Account\">\n"
            + "    <Hierarchy name=\"Account\" primaryKey=\"id_account\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_account\"/>\n"
            + "      <Level name=\"AcctType\" uniqueMembers=\"true\" column=\"acct_type\" approxRowCount=\"2\"/>\n"
            + "      <Level name=\"Account\" uniqueMembers=\"true\" column=\"id_account\"  type=\"Integer\" nameColumn=\"nm_account\" approxRowCount=\"6\"/>\n"
            + "    </Hierarchy>\n"
            + "    <Hierarchy name=\"AcctType\" primaryKey=\"id_account\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_account\"/>\n"
            + "      <Level name=\"AcctType\" uniqueMembers=\"true\" column=\"acct_type\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Customer\">\n"
            + "    <Hierarchy name=\"Customer\" primaryKey=\"id_customer\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_customer\"/>\n"
            + "      <Level name=\"Location\" uniqueMembers=\"true\" column=\"loc\" approxRowCount=\"2\"/>\n"
            + "      <Level name=\"Customer Name\" uniqueMembers=\"true\" column=\"id_customer\"  type=\"Integer\" nameColumn=\"nm_customer\" approxRowCount=\"4\"/>\n"
            + "    </Hierarchy>\n"
            + "    <Hierarchy name=\"Location\" primaryKey=\"id_customer\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_customer\"/>\n"
            + "      <Level name=\"Location\" uniqueMembers=\"true\" column=\"loc\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Date\">\n"
            + "    <Hierarchy name=\"Date\" primaryKey=\"ID_Date\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_date\"/>\n"
            + "      <Level name=\"Date\" uniqueMembers=\"true\" column=\"id_date\"  type=\"Integer\" nameColumn=\"nm_date\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"CustomerAccountBridge\" visible=\"false\">\n"
            + "    <Table name=\"m2m_bridge_accountcustomer\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_customer\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"id_customer\"/>\n"
            + "  </Cube>"
            + "\n"
            + "  <Cube name=\"M2M\">\n"
            + "    <Table name=\"m2m_fact_balance\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_account\" bridgeCube=\"CustomerAccountBridge\"/>\n"
            + "    <DimensionUsage name=\"Date\" source=\"Date\" foreignKey=\"id_date\"/>\n"
            + "    <Measure name=\"Amount\" aggregator=\"sum\" column=\"Amount\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"Amount\"/>\n"
            + "  </Cube>\n"
            + "</Schema>\n");
        return testContext;
    }

    protected TestContext createMultiJoinManyToManySchema() {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Gender\">\n"
            + "    <Hierarchy name=\"Gender\" primaryKey=\"id_gender\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_spending_gender_dim\"/>\n"
            + "      <Level name=\"Gender\" uniqueMembers=\"true\" column=\"id_gender\"  type=\"Integer\" nameColumn=\"nm_gender\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Year\">\n"
            + "    <Hierarchy name=\"Year\" primaryKey=\"id_year\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_spending_year_dim\"/>\n"
            + "      <Level name=\"Year\" uniqueMembers=\"true\" column=\"id_year\"  type=\"Integer\" approxRowCount=\"3\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Location\">\n"
            + "    <Hierarchy name=\"Location\" primaryKey=\"id_location\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_spending_location_dim\"/>\n"
            + "      <Level name=\"Location\" uniqueMembers=\"true\" column=\"id_location\"  type=\"Integer\" nameColumn=\"nm_location\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Category\">\n"
            + "    <Hierarchy name=\"Category\" primaryKey=\"id_category\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_spending_category_dim\"/>\n"
            + "      <Level name=\"Category\" uniqueMembers=\"true\" column=\"id_category\"  type=\"Integer\" nameColumn=\"nm_category\" approxRowCount=\"5\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"GenderYearCategoryBridge\" visible=\"false\">\n"
            + "    <Table name=\"m2m_spending_genderyear_category_bridge\"/>\n"
            + "    <DimensionUsage name=\"Gender\" source=\"Gender\" foreignKey=\"id_gender\"/>\n"
            + "    <DimensionUsage name=\"Year\" source=\"Year\" foreignKey=\"id_year\"/>\n"
            + "    <DimensionUsage name=\"Category\" source=\"Category\" foreignKey=\"id_category\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"id_category\"/>\n"
            + "  </Cube>"
            + "  <Cube name=\"GenderYearSpending\">\n"
            + "    <Table name=\"m2m_spending_genderyear_fact\"/>\n"
            + "    <DimensionUsage name=\"Gender\" source=\"Gender\" foreignKey=\"id_gender\"/>\n"
            + "    <DimensionUsage name=\"Year\" source=\"Year\" foreignKey=\"id_year\"/>\n"
            + "    <DimensionUsage name=\"Category\" source=\"Category\" foreignKey=\"id_gender\" bridgeCube=\"GenderYearCategoryBridge\"/>\n"
            + "    <DimensionUsage name=\"Location\" source=\"Location\" foreignKey=\"id_location\"/>\n"
            + "    <Measure name=\"Spending\" aggregator=\"sum\" column=\"spending\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"spending\"/>\n"
            + "  </Cube>\n"
            + "  <Cube name=\"CategorySpending\">\n"
            + "    <Table name=\"m2m_spending_category_fact\"/>\n"
            + "    <DimensionUsage name=\"Category\" source=\"Category\" foreignKey=\"id_category\"/>\n"
            + "    <DimensionUsage name=\"Gender\" source=\"Gender\" foreignKey=\"id_category\" bridgeCube=\"GenderYearCategoryBridge\"/>\n"
            + "    <DimensionUsage name=\"Year\" source=\"Year\" foreignKey=\"id_category\" bridgeCube=\"GenderYearCategoryBridge\"/>\n"
            + "    <DimensionUsage name=\"Location\" source=\"Location\" foreignKey=\"id_location\"/>\n"
            + "    <Measure name=\"Spending\" aggregator=\"sum\" column=\"spending\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"spending\"/>\n"
            + "  </Cube>\n"
            + "</Schema>\n");
        return testContext;
    }

    protected TestContext createMultiJoinManyToManyViewSchema() {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Gender\">\n"
            + "    <Hierarchy name=\"Gender\" primaryKey=\"id_gender\" hasAll=\"true\">\n"
            + "      <View alias=\"m2m_spending_gender_view\">\n"
            + "         <SQL dialect=\"generic\">SELECT * FROM m2m_spending_gender_dim</SQL>\n"
            + "      </View>\n"
            + "      <!-- <Table name=\"m2m_spending_gender_dim\"/> -->\n"
            + "      <Level name=\"Gender\" uniqueMembers=\"true\" column=\"id_gender\"  type=\"Integer\" nameColumn=\"nm_gender\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Year\">\n"
            + "    <Hierarchy name=\"Year\" primaryKey=\"id_year\" hasAll=\"true\">\n"
            + "      <View alias=\"m2m_spending_year_view\">\n"
            + "         <SQL dialect=\"generic\">SELECT * FROM m2m_spending_year_dim</SQL>\n"
            + "      </View>\n"
            + "      <!-- <Table name=\"m2m_spending_year_dim\"/> -->\n"
            + "      <Level name=\"Year\" uniqueMembers=\"true\" column=\"id_year\"  type=\"Integer\" approxRowCount=\"3\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Location\">\n"
            + "    <Hierarchy name=\"Location\" primaryKey=\"id_location\" hasAll=\"true\">\n"
            + "      <View alias=\"m2m_spending_location_view\">\n"
            + "         <SQL dialect=\"generic\">SELECT * FROM m2m_spending_location_dim</SQL>\n"
            + "      </View>\n"
            + "      <!-- <Table name=\"m2m_spending_location_dim\"/> -->\n"
            + "      <Level name=\"Location\" uniqueMembers=\"true\" column=\"id_location\"  type=\"Integer\" nameColumn=\"nm_location\" approxRowCount=\"2\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Category\">\n"
            + "    <Hierarchy name=\"Category\" primaryKey=\"id_category\" hasAll=\"true\">\n"
            + "      <View alias=\"m2m_spending_category_view\">\n"
            + "         <SQL dialect=\"generic\">SELECT * FROM m2m_spending_category_dim</SQL>\n"
            + "      </View>\n"          
            + "      <!-- <Table name=\"m2m_spending_category_dim\"/> -->\n"
            + "      <Level name=\"Category\" uniqueMembers=\"true\" column=\"id_category\"  type=\"Integer\" nameColumn=\"nm_category\" approxRowCount=\"5\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"GenderYearCategoryBridge\" visible=\"false\">\n"
            + "    <View alias=\"m2m_spending_genderyear_category_bridge_view\">\n"
            + "       <SQL dialect=\"generic\">SELECT * FROM m2m_spending_genderyear_category_bridge</SQL>\n"
            + "    </View>\n"
            + "    <!-- <Table name=\"m2m_spending_genderyear_category_bridge\"/> -->\n"
            + "    <DimensionUsage name=\"Gender\" source=\"Gender\" foreignKey=\"id_gender\"/>\n"
            + "    <DimensionUsage name=\"Year\" source=\"Year\" foreignKey=\"id_year\"/>\n"
            + "    <DimensionUsage name=\"Category\" source=\"Category\" foreignKey=\"id_category\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"id_category\"/>\n"
            + "  </Cube>"
            + "  <Cube name=\"GenderYearSpending\">\n"
            + "    <View alias=\"m2m_spending_genderyear_fact_view\">\n"
            + "       <SQL dialect=\"generic\">SELECT * FROM m2m_spending_genderyear_fact</SQL>\n"
            + "    </View>\n"
            + "    <!-- <Table name=\"m2m_spending_genderyear_fact\"/> -->\n"
            + "    <DimensionUsage name=\"Gender\" source=\"Gender\" foreignKey=\"id_gender\"/>\n"
            + "    <DimensionUsage name=\"Year\" source=\"Year\" foreignKey=\"id_year\"/>\n"
            + "    <DimensionUsage name=\"Category\" source=\"Category\" foreignKey=\"id_gender\" bridgeCube=\"GenderYearCategoryBridge\"/>\n"
            + "    <DimensionUsage name=\"Location\" source=\"Location\" foreignKey=\"id_location\"/>\n"
            + "    <Measure name=\"Spending\" aggregator=\"sum\" column=\"spending\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"spending\"/>\n"
            + "  </Cube>\n"
            + "  <Cube name=\"CategorySpending\">\n"
            + "    <View alias=\"m2m_spending_category_fact_view\">\n"
            + "       <SQL dialect=\"generic\">SELECT * FROM m2m_spending_category_fact</SQL>\n"
            + "    </View>\n"
            + "    <!-- <Table name=\"m2m_spending_category_fact\"/> -->\n"
            + "    <DimensionUsage name=\"Category\" source=\"Category\" foreignKey=\"id_category\"/>\n"
            + "    <DimensionUsage name=\"Gender\" source=\"Gender\" foreignKey=\"id_category\" bridgeCube=\"GenderYearCategoryBridge\"/>\n"
            + "    <DimensionUsage name=\"Year\" source=\"Year\" foreignKey=\"id_category\" bridgeCube=\"GenderYearCategoryBridge\"/>\n"
            + "    <DimensionUsage name=\"Location\" source=\"Location\" foreignKey=\"id_location\"/>\n"
            + "    <Measure name=\"Spending\" aggregator=\"sum\" column=\"spending\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"spending\"/>\n"
            + "  </Cube>\n"
            + "</Schema>\n");
        return testContext;
    }

    protected TestContext createBridgeCubeNotFoundContext() {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Account\">\n"
            + "    <Hierarchy name=\"Account\" primaryKey=\"id_account\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_account\"/>\n"
            + "      <Level name=\"Account\" uniqueMembers=\"true\" column=\"id_account\" type=\"Integer\" nameColumn=\"nm_account\" approxRowCount=\"6\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Customer\">\n"
            + "    <Hierarchy name=\"Customer\" primaryKey=\"id_customer\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_customer\"/>\n"
            + "      <Level name=\"Customer Name\" uniqueMembers=\"true\" column=\"id_customer\" type=\"Integer\" nameColumn=\"nm_customer\" approxRowCount=\"4\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"M2M\">\n"
            + "    <Table name=\"m2m_fact_balance\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_account\" bridgeCube=\"CustomerAccountBridge\"/>\n"
            + "    <Measure name=\"Amount\" aggregator=\"sum\" column=\"Amount\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"Amount\"/>\n"
            + "  </Cube>\n"
            + "</Schema>\n");
        return testContext;
    }

    protected TestContext createBridgeCubeLinkNotFoundContext() {
        final TestContext testContext = TestContext.instance().withSchema(
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"FoodMart\">\n"
            + "\n"
            + "  <Dimension name=\"Account\">\n"
            + "    <Hierarchy name=\"Account\" primaryKey=\"id_account\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_account\"/>\n"
            + "      <Level name=\"Account\" uniqueMembers=\"true\" column=\"id_account\" type=\"Integer\" nameColumn=\"nm_account\" approxRowCount=\"6\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Dimension name=\"Customer\">\n"
            + "    <Hierarchy name=\"Customer\" primaryKey=\"id_customer\" hasAll=\"true\">\n"
            + "      <Table name=\"m2m_dim_customer\"/>\n"
            + "      <Level name=\"Customer Name\" uniqueMembers=\"true\" column=\"id_customer\" type=\"Integer\" nameColumn=\"nm_customer\" approxRowCount=\"4\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "\n"
            + "  <Cube name=\"CustomerAccountBridge\" visible=\"false\">\n"
            + "    <Table name=\"m2m_bridge_accountcustomer\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_customer\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"id_customer\"/>\n"
            + "  </Cube>"
            + "\n"
            + "  <Cube name=\"M2M\">\n"
            + "    <Table name=\"m2m_fact_balance\"/>\n"
            + "    <DimensionUsage name=\"Account\" source=\"Account\" foreignKey=\"id_account\"/>\n"
            + "    <DimensionUsage name=\"Customer\" source=\"Customer\" foreignKey=\"id_account\" bridgeCube=\"CustomerAccountBridge\"/>\n"
            + "    <Measure name=\"Amount\" aggregator=\"sum\" column=\"Amount\"/>\n"
            + "    <Measure name=\"Count\" aggregator=\"count\" column=\"Amount\"/>\n"
            + "  </Cube>\n"
            + "</Schema>\n");
        return testContext;
    }

    public void testBridgeCubeNotFoundException() {
        TestContext context = createBridgeCubeNotFoundContext();
        context.assertQueryThrows(
            "select {[Measures].[Amount]} on columns \n"
            + "From [M2M]\n",
            "'CustomerAccountBridge' not found");
    }

    public void testBridgeCubeLinkNotFoundException() {
        TestContext context = createBridgeCubeLinkNotFoundContext();
        context.assertQueryThrows(
            "select {[Measures].[Amount]} on columns \n"
            + "From [M2M]\n",
            "Unable to locate bridge join");
    }

    public void testMultiJoinM2MScenarios() {
        testMultiJoinM2MScenarios(createMultiJoinManyToManySchema());
    }

    public void testMultiJoinM2MScenariosWithView() {
      testMultiJoinM2MScenarios(createMultiJoinManyToManyViewSchema());
  }

    public void testMultiJoinM2MScenarios(TestContext context) {
        // the category spending cube demonstrates multiple many to many
        // dimensions utilizing a single bridge table through a single join
        // dimension. note the differences between the two cube results below.
        // this is due to the behavior of how the relationships are defined
        // between category, year, and gender.  In the first example, category
        // is the joining dimension.  In the second example, gender and year
        // are the joining dimensions.

        context.assertQueryReturns(
            "select NON EMPTY {[Category].Members} on columns\n"
            + "from [CategorySpending] where {([Gender].[Female],[Year].[2014])}",
            "Axis #0:\n"
            + "{[Gender].[Female], [Year].[2014]}\n"
            + "Axis #1:\n"
            + "{[Category].[All Categorys]}\n"
            + "{[Category].[Category 2014]}\n"
            + "{[Category].[Category Year 2014 Female]}\n"
            + "Row #0: 1,750\n"
            + "Row #0: 1,025\n"
            + "Row #0: 725\n");

        context.assertQueryReturns(
            "select NON EMPTY {[Category].Members} on columns\n"
            + "from [GenderYearSpending] where {([Gender].[Female],[Year].[2014])}",
            "Axis #0:\n"
            + "{[Gender].[Female], [Year].[2014]}\n"
            + "Axis #1:\n"
            + "{[Category].[All Categorys]}\n"
            + "{[Category].[Category 2014]}\n"
            + "{[Category].[Category Year 2014 Female]}\n"
            + "Row #0: 725\n"
            + "Row #0: 725\n"
            + "Row #0: 725\n");

        context.assertQueryReturns(
            "select {[Category].Members} on columns\n"
            + "from [GenderYearSpending]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Category].[All Categorys]}\n"
            + "{[Category].[Category 2012]}\n"
            + "{[Category].[Category 2013]}\n"
            + "{[Category].[Category 2014]}\n"
            + "{[Category].[Category Year 2014 Female]}\n"
            + "{[Category].[Category Year 2014 Male]}\n"
            + "Row #0: 2,175\n"
            + "Row #0: 550\n"
            + "Row #0: 600\n"
            + "Row #0: 1,025\n"
            + "Row #0: 725\n"
            + "Row #0: 300\n");

        context.assertQueryReturns(
            "select NON EMPTY {[Gender].Members} on columns\n"
            + "from [GenderYearSpending] where {[Category].[Category 2014]}",
            "Axis #0:\n"
            + "{[Category].[Category 2014]}\n"
            + "Axis #1:\n"
            + "{[Gender].[All Genders]}\n"
            + "{[Gender].[Female]}\n"
            + "{[Gender].[Male]}\n"
            + "Row #0: 1,025\n"
            + "Row #0: 725\n"
            + "Row #0: 300\n");

        context.assertQueryReturns(
            "select NON EMPTY {[Year].Members} on columns\n"
            + "from [GenderYearSpending] where {[Category].[Category 2014]}",
            "Axis #0:\n"
            + "{[Category].[Category 2014]}\n"
            + "Axis #1:\n"
            + "{[Year].[All Years]}\n"
            + "{[Year].[2014]}\n"
            + "Row #0: 1,025\n"
            + "Row #0: 1,025\n");
    }

    public void testMultiJoinM2MSlicerWithView() {
      testMultiJoinM2MSlicer(createMultiJoinManyToManyViewSchema());
    }

    public void testMultiJoinM2MSlicer() {
      testMultiJoinM2MSlicer(createMultiJoinManyToManySchema());
    }

    public void testMultiJoinM2MSlicer(TestContext context) {
        context.assertQueryReturns(
            "select NonEmptyCrossJoin([Gender].[Gender].Members, [Year].[Year].Members) on columns\n"
            + "from [GenderYearSpending] where {[Category].[Category 2014],[Category].[Category Year 2014 Female]}",
            "Axis #0:\n"
            + "{[Category].[Category 2014]}\n"
            + "{[Category].[Category Year 2014 Female]}\n"
            + "Axis #1:\n"
            + "{[Gender].[Female], [Year].[2014]}\n"
            + "{[Gender].[Male], [Year].[2014]}\n"
            + "Row #0: 725\n"
            + "Row #0: 300\n");

        context.assertQueryReturns(
            "select NON EMPTY {[Gender].Members} on columns\n"
            + "from [GenderYearSpending] where {[Category].[Category 2014],[Category].[Category Year 2014 Female]}",
            "Axis #0:\n"
            + "{[Category].[Category 2014]}\n"
            + "{[Category].[Category Year 2014 Female]}\n"
            + "Axis #1:\n"
            + "{[Gender].[All Genders]}\n"
            + "{[Gender].[Female]}\n"
            + "{[Gender].[Male]}\n"
            + "Row #0: 1,025\n"
            + "Row #0: 725\n"
            + "Row #0: 300\n");
    }

    public void testNativeFilter() {
        boolean filter = prop.EnableNativeFilter.get();
        prop.EnableNativeFilter.set(true);
        TestContext context = createTestContext();
        context.assertQueryReturns(
            "Select\n"
            + "[Measures].[Amount] on columns,\n"
            + "Filter([Account].[All Accounts].Children, [Measures].[Amount] < 200) on rows\n"
            + "From [M2M] WHERE {([Date].[All Dates].[Day 1], [Customer].[All Customers].[Mark]),([Date].[All Dates].[Day 2], [Customer].[All Customers].[Paul])}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1], [Customer].[Mark]}\n"
            + "{[Date].[Day 2], [Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[Mark-Paul]}\n"
            + "Row #0: 100\n");

        prop.EnableNativeFilter.set(filter);
    }

    public void testNativeTopCount() {
        boolean topcount = prop.EnableNativeTopCount.get();
        prop.EnableNativeTopCount.set(true);
        TestContext context = createTestContext();
        context.assertQueryReturns(
            "Select\n"
            + "[Measures].[Amount] on columns,\n"
            + "TopCount([Account].[All Accounts].Children, 2, [Measures].[Amount]) on rows\n"
            + "From [M2M] WHERE {[Customer].[All Customers].[Mark],[Customer].[All Customers].[Paul]}\n",
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "Row #0: 205\n"
            + "Row #1: 205\n");
        prop.EnableNativeTopCount.set(topcount);
    }

    public void testSsasCompatibleNaming() {
        // this verifies that ssas compatible naming doesn't impact any of the
        // m2m logic
        boolean compat = prop.SsasCompatibleNaming.get();
        prop.SsasCompatibleNaming.set(true);
        TestContext context = createTestContext();

        // slicer test
        context.assertQueryReturns(
            "Select\n"
            + "[Measures].[Count] on columns,\n"
            + "[Account].[All Accounts] on rows\n"
            + "From [M2M] WHERE {[Customer].[All Customers].[Mark],[Customer].[All Customers].[Paul]}\n",
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "Row #0: 7\n");

        // virtual cube test
        context.assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount]} on columns,\n"
            + "NON EMPTY {[Account].[All Accounts], [Account].[All Accounts].Children} on rows\n"
            + "From [M2MVirtual] WHERE {([Customer].[All Customers].[Mark]),([Customer].[All Customers].[Paul])}\n",
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "Row #0: 715\n"
            + "Row #1: 205\n"
            + "Row #2: 100\n"
            + "Row #3: 205\n"
            + "Row #4: 205\n");
        prop.SsasCompatibleNaming.set(compat);
    }

    public void testMultiLevelQueries() {
        TestContext context = createMultiLevelTestContext(false);
        final String mdx =
            "Select\n"
            + "{[Measures].[Amount]} on columns,\n"
            + "{[Account].[Account].Members} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n";
        context.assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[One Person].[Luke]}\n"
            + "{[Account].[One Person].[Mark]}\n"
            + "{[Account].[One Person].[Paul]}\n"
            + "{[Account].[One Person].[Robert]}\n"
            + "{[Account].[Two People].[Mark-Paul]}\n"
            + "{[Account].[Two People].[Mark-Robert]}\n"
            + "Row #0: 100\n"
            + "Row #1: 100\n"
            + "Row #2: 100\n"
            + "Row #3: 100\n"
            + "Row #4: 100\n"
            + "Row #5: 100\n");

        context.assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[San Francisco].[Mark], [Customer].[All Customers].[Orlando].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[San Francisco].[Mark], [Customer].[All Customers].[Orlando].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n"
            + "WHERE {([Date].[All Dates].[Day 1],[Account].[Two People].[Mark-Paul]),([Date].[All Dates].[Day 1],[Account].[Two People].[Mark-Robert])}",
            "Axis #0:\n"
            + "{[Date].[Day 1], [Account].[Two People].[Mark-Paul]}\n"
            + "{[Date].[Day 1], [Account].[Two People].[Mark-Robert]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[San Francisco].[Mark]}\n"
            + "{[Customer].[Orlando].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 200\n"
            + "Row #0: 2\n"
            + "Row #1: 100\n"
            + "Row #1: 1\n"
            + "Row #2: 200\n"
            + "Row #2: 2\n");

        context.assertQueryReturns(
            "SELECT {[Customer].[All Customers], [Customer].[All Customers].Children} ON COLUMNS,\n"
            + "      {[Account].[All Accounts], [Account].[All Accounts].Children} ON ROWS\n"
            + "FROM [M2M] WHERE {[Date].[All Dates].[Day 1]}",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Customer].[All Customers]}\n"
            + "{[Customer].[Orlando]}\n"
            + "{[Customer].[San Francisco]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "{[Account].[One Person]}\n"
            + "{[Account].[Two People]}\n"
            + "Row #0: 600\n"
            + "Row #0: 400\n"
            + "Row #0: 400\n"
            + "Row #1: 400\n"
            + "Row #1: 200\n"
            + "Row #1: 200\n"
            + "Row #2: 200\n"
            + "Row #2: 200\n"
            + "Row #2: 200\n");
    }

    public void testMultiHierarchyQueries() {
        TestContext context = createMultiHierarchyTestContext();
        context.assertQueryReturns(
            "Select\n"
            + "{[Measures].[Amount]} on columns,\n"
            + "{[Account].[Account].Members} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[One Person].[Luke]}\n"
            + "{[Account].[One Person].[Mark]}\n"
            + "{[Account].[One Person].[Paul]}\n"
            + "{[Account].[One Person].[Robert]}\n"
            + "{[Account].[Two People].[Mark-Paul]}\n"
            + "{[Account].[Two People].[Mark-Robert]}\n"
            + "Row #0: 100\n"
            + "Row #1: 100\n"
            + "Row #2: 100\n"
            + "Row #3: 100\n"
            + "Row #4: 100\n"
            + "Row #5: 100\n");

        context.assertQueryReturns(
            "Select\n"
            + "{[Measures].[Amount]} on columns,\n"
            + "{[Account.AcctType].[AcctType].Members} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account.AcctType].[One Person]}\n"
            + "{[Account.AcctType].[Two People]}\n"
            + "Row #0: 400\n"
            + "Row #1: 200\n");

        context.assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[San Francisco].[Mark], [Customer].[All Customers].[Orlando].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[San Francisco].[Mark], [Customer].[All Customers].[Orlando].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n"
            + "WHERE {([Date].[All Dates].[Day 1],[Account].[Two People].[Mark-Paul]),([Date].[All Dates].[Day 1],[Account].[Two People].[Mark-Robert])}",
            "Axis #0:\n"
            + "{[Date].[Day 1], [Account].[Two People].[Mark-Paul]}\n"
            + "{[Date].[Day 1], [Account].[Two People].[Mark-Robert]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[San Francisco].[Mark]}\n"
            + "{[Customer].[Orlando].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 200\n"
            + "Row #0: 2\n"
            + "Row #1: 100\n"
            + "Row #1: 1\n"
            + "Row #2: 200\n"
            + "Row #2: 2\n");

        context.assertQueryReturns(
            "SELECT {[Customer.Location].Members} ON COLUMNS,\n"
            + "      {[Account.AcctType].Members} ON ROWS\n"
            + "FROM [M2M] WHERE {[Date].[All Dates].[Day 1]}",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Customer.Location].[All Customer.Locations]}\n"
            + "{[Customer.Location].[Orlando]}\n"
            + "{[Customer.Location].[San Francisco]}\n"
            + "Axis #2:\n"
            + "{[Account.AcctType].[All Account.AcctTypes]}\n"
            + "{[Account.AcctType].[One Person]}\n"
            + "{[Account.AcctType].[Two People]}\n"
            + "Row #0: 600\n"
            + "Row #0: 400\n"
            + "Row #0: 400\n"
            + "Row #1: 400\n"
            + "Row #1: 200\n"
            + "Row #1: 200\n"
            + "Row #2: 200\n"
            + "Row #2: 200\n"
            + "Row #2: 200\n");
    }

    public void _testAggTableWithAggLevel() {
        // until AggLevel is supported for many to many dims, these tests should
        // fail, returning incorrect roll up results.  The issue is that
        // information in the agg table cannot be rolled up, similar to distinct
        // count behavior.  We could implement a "don't roll up" policy on agg
        // tables to allow for usage, but that would be of limited value because
        // only queries that request exactly the dimensions at play would be
        // relevant.

        boolean origUseAgg = prop.UseAggregates.get();
        boolean origReadAgg = prop.ReadAggregates.get();
        prop.UseAggregates.set(true);
        prop.ReadAggregates.set(true);

        TestContext context = createMultiLevelTestContext(true);
        context.assertQueryReturns(
            "SELECT {[Customer].[All Customers], [Customer].[All Customers].Children} ON COLUMNS,\n"
            + "      {[Account].[All Accounts], [Account].[All Accounts].Children} ON ROWS\n"
            + "FROM [M2M] WHERE {[Date].[All Dates].[Day 1]}",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Customer].[All Customers]}\n"
            + "{[Customer].[Orlando]}\n"
            + "{[Customer].[San Francisco]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "{[Account].[One Person]}\n"
            + "{[Account].[Two People]}\n"
            + "Row #0: 600\n"
            + "Row #0: 400\n"
            + "Row #0: 400\n"
            + "Row #1: 400\n"
            + "Row #1: 200\n"
            + "Row #1: 200\n"
            + "Row #2: 200\n"
            + "Row #2: 200\n"
            + "Row #2: 200\n");

        prop.UseAggregates.set(origUseAgg);
        prop.ReadAggregates.set(origReadAgg);
    }

    public void testDistinctCountMeasure() {
        // note that it's not possible to do a distinct count on a
        // many to many dimension directly, because there is no direct foreign
        // key column in the  fact that is related to the many to many
        // dimension. this test verifies that use against other columns still
        // function correctly.
        getTestContext().assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Distinct Account Count],[Measures].[Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Distinct Account Count]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 3\n"
            + "Row #0: 5\n"
            + "Row #1: 2\n"
            + "Row #1: 3\n"
            + "Row #2: 4\n"
            + "Row #2: 7\n");
    }

    public void testWithRoles() {
        // In this first test scenario, the user has access to Customer
        // (which joins through the account dimension)
        // but does not have access directly to the account dimension itself.
        TestContext context =
            createMultiLevelTestContext(false).withRole("role_test_1");

        // verify we cannot access the account dimension
        context.assertQueryThrows(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Account].[One Person].[Luke]}\n",
            "object '[Account].[One Person].[Luke]' not found");

        context.assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Orlando].[Paul]}\n"
            + "{[Customer].[Orlando].[Robert]}\n"
            + "{[Customer].[San Francisco].[Luke]}\n"
            + "{[Customer].[San Francisco].[Mark]}\n"
            + "{[Customer].[All Customers]}\n"
            + "Row #0: 200\n"
            + "Row #0: 2\n"
            + "Row #1: 200\n"
            + "Row #1: 2\n"
            + "Row #2: 100\n"
            + "Row #2: 1\n"
            + "Row #3: 300\n"
            + "Row #3: 3\n"
            + "Row #4: 600\n"
            + "Row #4: 6\n");

        // this test confirms that the many to many aggregation approach
        // still functions even without access to the account dimension
        context.assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[San Francisco].[Mark], [Customer].[All Customers].[Orlando].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[San Francisco].[Mark], [Customer].[All Customers].[Orlando].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n"
            + "WHERE {[Date].[All Dates].[Day 1]}",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[San Francisco].[Mark]}\n"
            + "{[Customer].[Orlando].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 300\n"
            + "Row #0: 3\n"
            + "Row #1: 200\n"
            + "Row #1: 2\n"
            + "Row #2: 400\n"
            + "Row #2: 4\n");

        // test hierarchy restrictions with full rollup
        context = createMultiLevelTestContext(false).withRole("role_test_2");
        context.assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[San Francisco].[Luke]}\n"
            + "{[Customer].[All Customers]}\n"
            + "Row #0: 100\n"
            + "Row #0: 1\n"
            + "Row #1: 600\n"
            + "Row #1: 6\n");

        // test hierarchy restrictions with partial rollup
        context = createMultiLevelTestContext(false).withRole("role_test_3");
        context.assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[San Francisco].[Luke]}\n"
            + "{[Customer].[All Customers]}\n"
            + "Row #0: 100\n"
            + "Row #0: 1\n"
            + "Row #1: 100\n"
            + "Row #1: 1\n");

        // test hierarchy restrictions with partial rollup
        context = createMultiLevelTestContext(false).withRole("role_test_4");
        context.assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[San Francisco].[Luke]}\n"
            + "Row #0: 100\n"
            + "Row #0: 1\n");
    }

    public void testAggTableWithForeignKeyLink() {
        getConnection().getCacheControl(null).flushSchemaCache();

        boolean origUseAgg = prop.UseAggregates.get();
        boolean origReadAgg = prop.ReadAggregates.get();
        prop.UseAggregates.set(true);
        prop.ReadAggregates.set(true);

        TestContext context = createTestContext();

        // make sure schema cache is cleared so we get a db hit.
        // reuse the datasource change listener logger due to issues
        // with package visibility
        DataSourceChangeListenerTest.SqlLogger logger =
            new DataSourceChangeListenerTest.SqlLogger();
        RolapUtil.setHook(logger);
        context.assertQueryReturns(
            "Select\n"
            + "{[Measures].[Amount]} on columns,\n"
            + "{[Account].[Account].Members} on rows\n"
            + "From [M2M]\n",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[Luke]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "{[Account].[Robert]}\n"
            + "Row #0: 205\n"
            + "Row #1: 205\n"
            + "Row #2: 100\n"
            + "Row #3: 205\n"
            + "Row #4: 205\n"
            + "Row #5: 205\n");
        boolean foundAggQuery = false;
        for (String sql : logger.getSqlQueries()) {
            if (sql.indexOf("m2m_fact_balance_date_agg") >= 0) {
                foundAggQuery = true;
            }
        }
        Assert.assertTrue(
            "test if m2m_fact_balance_date_agg present in queries.",
            foundAggQuery);
        getConnection().getCacheControl(null).flushSchemaCache();
        prop.UseAggregates.set(origUseAgg);
        prop.ReadAggregates.set(origReadAgg);
    }

    public void testStandardQueryAgainstM2MSchema() {
        final String mdx =
            "Select\n"
            + "{[Measures].[Amount]} on columns,\n"
            + "{[Account].[Account].Members} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[Luke]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "{[Account].[Robert]}\n"
            + "Row #0: 100\n"
            + "Row #1: 100\n"
            + "Row #2: 100\n"
            + "Row #3: 100\n"
            + "Row #4: 100\n"
            + "Row #5: 100\n");
    }

    private final MondrianProperties prop = MondrianProperties.instance();

    public void testVirtualCubeUnrelatedDimensions() {
        boolean origIgnoreMeasure =
            prop.IgnoreMeasureForNonJoiningDimension.get();
        prop.IgnoreMeasureForNonJoiningDimension.set(true);

        getTestContext().assertQueryReturns(
            "WITH MEMBER [Measures].[Avg Amount] AS '[Measures].[Amount] / [Measures].[Total Count]'\n"
            + "MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Avg Amount]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2MVirtual]\n"
            + "WHERE {([Date].[All Dates].[Day 1],[Account].[Mark-Paul]),([Date].[All Dates].[Day 1],[Account].[Mark-Robert])}",
            "Axis #0:\n"
            + "{[Date].[Day 1], [Account].[Mark-Paul]}\n"
            + "{[Date].[Day 1], [Account].[Mark-Robert]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Avg Amount]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 100\n"
            + "Row #1: 100\n"
            + "Row #2: 200\n");

        // this query does not bring in unrelated dimension just tests
        // basic useage of virtual cube with unrelated dimensions
        getTestContext().assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Total Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2MVirtual]\n"
            + "WHERE {[Date].[All Dates].[Day 1]}",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Total Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 300\n"
            + "Row #0: 3\n"
            + "Row #1: 200\n"
            + "Row #1: 2\n"
            + "Row #2: 400\n"
            + "Row #2: 4\n");

        prop.IgnoreMeasureForNonJoiningDimension.set(origIgnoreMeasure);
    }

    public void testM2MRollupInCache() {
        // Note this demonstrates the "allowRollup=false" behavior
        // in the SegmentColumn.  Many to many dimensions cannot be
        // rolled up.

        getTestContext().assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount]} on columns,\n"
            + "NON EMPTY {[Customer].[All Customers].Children} on rows\n"
            + "From [M2M]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Luke]}\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Robert]}\n"
            + "Row #0: 205\n"
            + "Row #1: 510\n"
            + "Row #2: 305\n"
            + "Row #3: 410\n");
        getTestContext().assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount]} on columns,\n"
            + "NON EMPTY {[Account].[All Accounts]} on rows\n"
            + "From [M2M]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "Row #0: 1,125\n");
    }

    public void testVirtualCubeQueries() {
        // regular cube results should match virtual cube results
        getTestContext().assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount]} on columns,\n"
            + "NON EMPTY {[Account].[All Accounts], [Account].[All Accounts].Children} on rows\n"
            + "From [M2MVirtual] WHERE {([Customer].[All Customers].[Mark]),([Customer].[All Customers].[Paul])}\n",
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "Row #0: 715\n"
            + "Row #1: 205\n"
            + "Row #2: 100\n"
            + "Row #3: 205\n"
            + "Row #4: 205\n");

        getTestContext().assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Total Count]} on columns,\n"
            + "NON EMPTY {[Account].[All Accounts], [Account].[All Accounts].Children} on rows\n"
            + "From [M2MCount] WHERE {([Customer].[All Customers].[Mark]),([Customer].[All Customers].[Paul])}\n",
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Total Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "Row #0: 4\n"
            + "Row #1: 1\n"
            + "Row #2: 1\n"
            + "Row #3: 1\n"
            + "Row #4: 1\n");

        getTestContext().assertQueryReturns(
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Total Count]} on columns,\n"
            + "NON EMPTY {[Account].[All Accounts], [Account].[All Accounts].Children} on rows\n"
            + "From [M2MVirtual] WHERE {([Customer].[All Customers].[Mark]),([Customer].[All Customers].[Paul])}\n",
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Total Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "Row #0: 715\n"
            + "Row #0: 4\n"
            + "Row #1: 205\n"
            + "Row #1: 1\n"
            + "Row #2: 100\n"
            + "Row #2: 1\n"
            + "Row #3: 205\n"
            + "Row #3: 1\n"
            + "Row #4: 205\n"
            + "Row #4: 1\n");

        getTestContext().assertQueryReturns(
            "Select\n"
            + "{[Measures].[Amount], [Measures].[Total Count]} on columns,\n"
            + "{[Account].[All Accounts]} on rows\n"
            + "From [M2MVirtual]\n",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Total Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "Row #0: 1,125\n"
            + "Row #0: 6\n");

        getTestContext().assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Total Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2MVirtual]\n"
            + "WHERE {([Account].[Mark-Paul]),([Account].[Mark-Robert])}",
            "Axis #0:\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Total Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 305\n"
            + "Row #0: 2\n"
            + "Row #1: 100\n"
            + "Row #1: 1\n"
            + "Row #2: 305\n"
            + "Row #2: 2\n");
    }

    public void testM2MAndJoiningDimQuery() {
        final String mdx =
            "Select\n"
            + "{[Account].[Account].Members, [Account].[All Accounts]} on columns,\n"
            + "{[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Account].[Luke]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "{[Account].[Robert]}\n"
            + "{[Account].[All Accounts]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Luke]}\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Robert]}\n"
            + "{[Customer].[All Customers]}\n"
            + "Row #0: 100\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 100\n"
            + "Row #1: \n"
            + "Row #1: 100\n"
            + "Row #1: 100\n"
            + "Row #1: 100\n"
            + "Row #1: \n"
            + "Row #1: \n"
            + "Row #1: 300\n"
            + "Row #2: \n"
            + "Row #2: \n"
            + "Row #2: 100\n"
            + "Row #2: \n"
            + "Row #2: 100\n"
            + "Row #2: \n"
            + "Row #2: 200\n"
            + "Row #3: \n"
            + "Row #3: \n"
            + "Row #3: \n"
            + "Row #3: 100\n"
            + "Row #3: \n"
            + "Row #3: 100\n"
            + "Row #3: 200\n"
            + "Row #4: 100\n"
            + "Row #4: 100\n"
            + "Row #4: 100\n"
            + "Row #4: 100\n"
            + "Row #4: 100\n"
            + "Row #4: 100\n"
            + "Row #4: 600\n");
    }

    public void testNonEmptyM2MAndJoiningDimQuery() {
        final String mdx =
            "Select\n"
            + "NON EMPTY {[Account].[Account].Members, [Account].[All Accounts]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 2]}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 2]}\n"
            + "Axis #1:\n"
            + "{[Account].[Luke]}\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "{[Account].[Robert]}\n"
            + "{[Account].[All Accounts]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Luke]}\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Robert]}\n"
            + "{[Customer].[All Customers]}\n"
            + "Row #0: 105\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 105\n"
            + "Row #1: \n"
            + "Row #1: 105\n"
            + "Row #1: 105\n"
            + "Row #1: \n"
            + "Row #1: \n"
            + "Row #1: 210\n"
            + "Row #2: \n"
            + "Row #2: \n"
            + "Row #2: \n"
            + "Row #2: 105\n"
            + "Row #2: \n"
            + "Row #2: 105\n"
            + "Row #3: \n"
            + "Row #3: \n"
            + "Row #3: 105\n"
            + "Row #3: \n"
            + "Row #3: 105\n"
            + "Row #3: 210\n"
            + "Row #4: 105\n"
            + "Row #4: 105\n"
            + "Row #4: 105\n"
            + "Row #4: 105\n"
            + "Row #4: 105\n"
            + "Row #4: 525\n");
    }

    public void testM2MDimRollup() {
        final String mdx =
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Customer].[Customer Name].Members, [Customer].[All Customers]} on rows\n"
            + "From [M2M] WHERE {[Date].[All Dates].[Day 1]}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Luke]}\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Robert]}\n"
            + "{[Customer].[All Customers]}\n"
            + "Row #0: 100\n"
            + "Row #0: 1\n"
            + "Row #1: 300\n"
            + "Row #1: 3\n"
            + "Row #2: 200\n"
            + "Row #2: 2\n"
            + "Row #3: 200\n"
            + "Row #3: 2\n"
            + "Row #4: 600\n"
            + "Row #4: 6\n");
    }

    public void testM2MInSlicer() {
        final String mdx =
            "Select\n"
            + "[Measures].[Count] on columns,\n"
            + "[Account].[All Accounts] on rows\n"
            + "From [M2M] WHERE {[Customer].[All Customers].[Mark],[Customer].[All Customers].[Paul]}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "Row #0: 7\n");
    }

    public void testM2MInSlicerAsTuple() {
        final String mdx =
            "Select\n"
            + "[Measures].[Count] on columns,\n"
            + "[Account].[All Accounts] on rows\n"
            + "From [M2M] WHERE {([Date].[All Dates].[Day 1], [Customer].[All Customers].[Mark]),([Date].[All Dates].[Day 1], [Customer].[All Customers].[Paul])}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 1], [Customer].[Mark]}\n"
            + "{[Date].[Day 1], [Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "Row #0: 4\n");
    }

    public void testM2MRollup() {
        final String mdx =
            "Select\n"
            + "[Measures].[Count] on columns,\n"
            + "[Account].[All Accounts] on rows\n"
            + "From [M2M]\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[All Accounts]}\n"
            + "Row #0: 11\n");
    }

    public void testM2MInSlicerWithChildrenFunction() {
        final String mdx =
            "Select\n"
            + "NON EMPTY {[Measures].[Amount], [Measures].[Count]} on columns,\n"
            + "NON EMPTY {[Account].[All Accounts].Children} on rows\n"
            + "From [M2M] WHERE {([Date].[All Dates].[Day 1], [Customer].[All Customers].[Mark]),([Date].[All Dates].[Day 1], [Customer].[All Customers].[Paul])}\n";
        getTestContext().assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{[Date].[Day 1], [Customer].[Mark]}\n"
            + "{[Date].[Day 1], [Customer].[Paul]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Account].[Mark]}\n"
            + "{[Account].[Mark-Paul]}\n"
            + "{[Account].[Mark-Robert]}\n"
            + "{[Account].[Paul]}\n"
            + "Row #0: 100\n"
            + "Row #0: 1\n"
            + "Row #1: 100\n"
            + "Row #1: 1\n"
            + "Row #2: 100\n"
            + "Row #2: 1\n"
            + "Row #3: 100\n"
            + "Row #3: 1\n");
    }

    public void testAggregateFunction() {
        getTestContext().assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n"
            + "WHERE ([Date].[All Dates].[Day 1])",
            "Axis #0:\n"
            + "{[Date].[Day 1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 300\n"
            + "Row #0: 3\n"
            + "Row #1: 200\n"
            + "Row #1: 2\n"
            + "Row #2: 400\n"
            + "Row #2: 4\n");
    }

    public void testAggregatedCalMeasure() {
        getTestContext().assertQueryReturns(
            "WITH MEMBER [Measures].[Avg Amount] AS '[Measures].[Amount] / [Measures].[Count]'\n"
            + "MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Avg Amount]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n"
            + "WHERE {([Date].[All Dates].[Day 1],[Account].[Mark-Paul]),([Date].[All Dates].[Day 1],[Account].[Mark-Robert])}",
            "Axis #0:\n"
            + "{[Date].[Day 1], [Account].[Mark-Paul]}\n"
            + "{[Date].[Day 1], [Account].[Mark-Robert]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Avg Amount]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 100\n"
            + "Row #1: 100\n"
            + "Row #2: 100\n");
    }

    public void testAggregateFunctionAndSlicer() {
        getTestContext().assertQueryReturns(
            "WITH MEMBER [Customer].[Mark and Paul] AS 'AGGREGATE({[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul]})'\n"
            + "SELECT {[Measures].[Amount], [Measures].[Count]} ON COLUMNS,\n"
            + "      {[Customer].[All Customers].[Mark], [Customer].[All Customers].[Paul], [Customer].[Mark and Paul]} ON ROWS\n"
            + "FROM [M2M]\n"
            + "WHERE {([Date].[All Dates].[Day 1],[Account].[Mark-Paul]),([Date].[All Dates].[Day 1],[Account].[Mark-Robert])}",
            "Axis #0:\n"
            + "{[Date].[Day 1], [Account].[Mark-Paul]}\n"
            + "{[Date].[Day 1], [Account].[Mark-Robert]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Amount]}\n"
            + "{[Measures].[Count]}\n"
            + "Axis #2:\n"
            + "{[Customer].[Mark]}\n"
            + "{[Customer].[Paul]}\n"
            + "{[Customer].[Mark and Paul]}\n"
            + "Row #0: 200\n"
            + "Row #0: 2\n"
            + "Row #1: 100\n"
            + "Row #1: 1\n"
            + "Row #2: 200\n"
            + "Row #2: 2\n");
    }

    public void testAggregateFunDefCartesianLogic() {
        Member a = new DummyMember("A");
        Member b = new DummyMember("B");
        Member c = new DummyMember("C");
        Member[] mlist = new Member[6];
        mlist[0] = a;
        mlist[1] = b;
        mlist[2] = c;

        TupleList newList = new ListTupleList(
            mlist.length,
            new ArrayList<Member>());

        List<List<List<Member>>> newMembersList =
            new ArrayList<List<List<Member>>>();
        List<List<Member>> bSet = new ArrayList<List<Member>>();
        bSet.add(Collections.singletonList((Member)new DummyMember("D")));
        bSet.add(Collections.singletonList((Member)new DummyMember("E")));

        List<List<Member>> cSet = new ArrayList<List<Member>>();
        List<Member> cSetList1 = new ArrayList<Member>();
        cSetList1.add(new DummyMember("F"));
        cSetList1.add(new DummyMember("G"));
        cSet.add(cSetList1);
        List<Member> cSetList2 = new ArrayList<Member>();
        cSetList2.add(new DummyMember("H"));
        cSetList2.add(new DummyMember("I"));
        cSet.add(cSetList2);
        newMembersList.add(cSet);
        newMembersList.add(bSet);

        TestManyToManyUtil.buildCartesianProduct(
            newMembersList, newList, mlist);

        TestContext.assertEqualsVerbose(
            "[[A, B, C, F, G, D], [A, B, C, H, I, D], [A, B, C, F, G, E], "
            + "[A, B, C, H, I, E]]",
            newList.toString());

        newList.clear();
        newMembersList.clear();
        newMembersList.add(bSet);
        newMembersList.add(cSet);

        TestManyToManyUtil.buildCartesianProduct(
            newMembersList, newList, mlist);

        // test newList
        TestContext.assertEqualsVerbose(
            "[[A, B, C, D, F, G], [A, B, C, E, F, G], [A, B, C, D, H, I], "
            + "[A, B, C, E, H, I]]",
            newList.toString());
    }

    /**
     * This class overrides the buildCartesianProduct function to public so it
     * can be tested
     */
    static class TestManyToManyUtil extends ManyToManyUtil {
        public static void buildCartesianProduct(
            List<List<List<Member>>> newMembersList,
            TupleList newList,
            Member[] mlist)
        {
            ManyToManyUtil.buildCartesianProduct(
                newMembersList, newList, mlist);
        }
    }

    static class DummyMember implements Member {

        public String name;

        public String toString() {
            return name;
        }

        public DummyMember(String name) {
            this.name = name;
        }

        @Override
        public String getUniqueName() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public OlapElement lookupChild(
            SchemaReader schemaReader, Segment s, MatchType matchType)
        {
            return null;
        }

        @Override
        public String getQualifiedName() {
            return null;
        }

        @Override
        public String getCaption() {
            return null;
        }

        @Override
        public String getLocalized(LocalizedProperty prop, Locale locale) {
            return null;
        }

        @Override
        public Dimension getDimension() {
            return null;
        }

        @Override
        public boolean isVisible() {
            return false;
        }

        @Override
        public int compareTo(Object arg0) {
            return 0;
        }

        @Override
        public Map<String, Annotation> getAnnotationMap() {
            return null;
        }

        @Override
        public Member getParentMember() {
            return null;
        }

        @Override
        public Level getLevel() {
            return null;
        }

        @Override
        public Hierarchy getHierarchy() {
            return null;
        }

        @Override
        public String getParentUniqueName() {
            return null;
        }

        @Override
        public MemberType getMemberType() {
            return null;
        }

        @Override
        public boolean isParentChildLeaf() {
            return false;
        }

        @Override
        public void setName(String name) {
        }

        @Override
        public boolean isAll() {
            return false;
        }

        @Override
        public boolean isMeasure() {
            return false;
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean isChildOrEqualTo(Member member) {
            return false;
        }

        @Override
        public boolean isCalculated() {
            return false;
        }

        @Override
        public boolean isEvaluated() {
            return false;
        }

        @Override
        public int getSolveOrder() {
            return 0;
        }

        @Override
        public Exp getExpression() {
            return null;
        }

        @Override
        public List<Member> getAncestorMembers() {
            return null;
        }

        @Override
        public boolean isCalculatedInQuery() {
            return false;
        }

        @Override
        public Object getPropertyValue(String propertyName) {
            return null;
        }

        @Override
        public Object getPropertyValue(String propertyName, boolean matchCase) {
            return null;
        }

        @Override
        public String getPropertyFormattedValue(String propertyName) {
            return null;
        }

        @Override
        public void setProperty(String name, Object value) {}

        @Override
        public Property[] getProperties() {
            return null;
        }

        @Override
        public int getOrdinal() {
            return 0;
        }

        @Override
        public Comparable getOrderKey() {
            return null;
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        @Override
        public int getDepth() {
            return 0;
        }

        @Override
        public Member getDataMember() {
            return null;
        }
    }
}
// End ManyToManyTest.java