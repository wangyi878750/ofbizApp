package org.ofbiz.payroll;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javolution.util.FastMap;

import org.apache.log4j.Logger;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.webapp.event.EventHandlerException;

public class CreatePayrollPeriods {
	
	private static Logger log = Logger.getLogger(PayrollProcess.class);
		
	static Timestamp curTimeStamp = null;
	static Timestamp newPeriodEndDate = null;

	public static String createPayrollPeriod(HttpServletRequest request,
			HttpServletResponse response) 
	{
		Map<String, Object> result = FastMap.newInstance();
		Delegator delegator = (Delegator) request.getAttribute("delegator");

		String payrollYearId = (String) request.getParameter("payrollYearId");
		
		// Get employees
		List<GenericValue> yearsELI = null;
		
		log.info("######### payrollYearId :::: " + payrollYearId);

		try {
			yearsELI = delegator.findList("PayrollYear", EntityCondition
					.makeCondition("payrollYearId", payrollYearId), null,
					null, null, false);
		
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		int cnt=getPeriodCount(payrollYearId, delegator);
		if(cnt==0)
		{
			for (GenericValue payrollYear : yearsELI) 
			{
				log.info("Create Periods#######################1");
				createStdPeriods(payrollYear.getString("name"), payrollYearId, null, delegator);
			}
		}
		else if(cnt>0 && cnt!=12)
		{
			deletePeriods(payrollYearId, delegator);
			
			for (GenericValue payrollYear : yearsELI) 
			{
				log.info("Create Periods#######################2");
				createStdPeriods(payrollYear.getString("name"), payrollYearId, null, delegator);
			}
		}
		else
		{
			log.info("No new Periods#######################");
		}

		
			
			/*genericValue.setString("closed", "Y");
			try {
				delegator.createOrStore(genericValue);
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
			
		Writer out;
		try {
			out = response.getWriter();
			out.write("");
			out.flush();
		} catch (IOException e) {
			try {
				throw new EventHandlerException(
						"Unable to get response writer", e);
			} catch (EventHandlerException e1) {
				e1.printStackTrace();
			}
		}
		return "";				
	}
	
	
	private static void deletePeriods(String payrollYearId, Delegator delegator) {
		List<GenericValue> listPeriods = getPeriods(payrollYearId, delegator);
		
		deletePayrollPeriods(listPeriods, payrollYearId, delegator);
		
	}


	private static void deletePayrollPeriods(List<GenericValue> listPeriods,
			String payrollYearId, Delegator delegator) {
		for (GenericValue genericValue : listPeriods) {
			deleteRecord(delegator, genericValue.getString("payrollPeriodId"), payrollYearId );
		}
		
	}

	private static void deleteRecord(Delegator delegator, String payrollPeriodId,
			String payrollYearId) {
		// TODO Auto-generated method stub
		List<GenericValue> payrollPeriodELI = null;
		
		EntityConditionList<EntityExpr> payrollPeriodConditions = EntityCondition
		.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
				"payrollYearId", EntityOperator.EQUALS, payrollYearId),
				EntityCondition.makeCondition("payrollPeriodId",
						EntityOperator.EQUALS, payrollPeriodId)),
				EntityOperator.AND);
		
		try {
			payrollPeriodELI = delegator.findList("PayrollPeriod",
					payrollPeriodConditions, null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		try {
			delegator.removeAll(payrollPeriodELI);
		} catch (GenericEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


	private static List<GenericValue> getPeriods(String payrollYearId,
			Delegator delegator) {
		List<GenericValue> payrollPeriodELI = new LinkedList<GenericValue>();
		try {
			payrollPeriodELI = delegator.findList("PayrollPeriod",
					EntityCondition.makeCondition("payrollYearId",
							payrollYearId),
					null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		return payrollPeriodELI;
	}


	private static int getPeriodCount(String payrollYearId, Delegator delegator) {
		int count=0;
		List<GenericValue> payrollPeriodELI = new LinkedList<GenericValue>();
		try {
			payrollPeriodELI = delegator.findList("PayrollPeriod",
					EntityCondition.makeCondition("payrollYearId",
							payrollYearId),
					null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		for (GenericValue payrollPeriod : payrollPeriodELI) {
			count++;
		}
		
		log.info("Count Is #######################"+count);
		return count;
	}



	public static String createStdPeriods(String yearName, String payrollYearId, Locale locale, Delegator delegator)
	{
		int year=0;
		String[] months = null;
		months = new String[]{"Jan", "Feb", "Mar",
				"Apr", "May", "Jun",
				"Jul", "Aug", "Sep",
				"Oct", "Nov", "Dec"};
		
		List<GenericValue> listPayrollPeriods = new ArrayList<GenericValue>();
		/*try
		{
			DateFormatSymbols symbols = new DateFormatSymbols(locale);
			months = symbols.getShortMonths();
		}
		catch (Exception e)
		{
			
		}*/
		//GET YEAR AS INT FROM PAYROLLYEAR
		try
		{
			if (yearName.length() >= 4)
				yearName = yearName.substring(0, 4);
			else
				yearName="";
				
			
			year = Integer.parseInt(yearName);
		}
		catch(NumberFormatException e)
		{
			return "The value cannot be taken as a Year";
		}
		
		GregorianCalendar cal = new GregorianCalendar();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		//
		for (int month = 0; month < 12; month++)
		{
			cal.set(Calendar.YEAR, year);
			cal.set(Calendar.MONTH, month);
			cal.set(Calendar.DAY_OF_MONTH, 1);
			Timestamp start = new Timestamp(cal.getTimeInMillis());
			
			String name = months[month] + "-" + getYY(year);
			//
			cal.add(Calendar.MONTH, 1);
			cal.add(Calendar.DAY_OF_YEAR, -1);
			Timestamp end = new Timestamp(cal.getTimeInMillis());
			
			log.info("######### Period Name ::::"+name);
			log.info("######### Start :::: " + start+"<><><><><><>>><>END><><><><>"+end);
			
			listPayrollPeriods.add(createPeriod(month+1, name, payrollYearId, start, end, delegator));
		}
		try {
			delegator.storeAll(listPayrollPeriods);
		} catch (GenericEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}
	
	private static GenericValue createPeriod(int seq, String periodName, String payrollYearId, Timestamp start, Timestamp end, Delegator delegator) {
		
		String payrollPeriodSequenceId = delegator.getNextSeqId("PayrollPeriod");
		Long sequence = new Long(seq);

		GenericValue payrollPeriods = delegator.makeValidValue(
				"PayrollPeriod", UtilMisc.toMap(
						"payrollPeriodId", payrollPeriodSequenceId, 
						"payrollYearId", payrollYearId, 
						"sequence_no", sequence, 
						"name", periodName, 
						"currentperiod", "N", 
						"status", "Inactive",
						"startDate", start,
						"endDate", end,
						"payrollcheck", null));
		try {
			payrollPeriods = delegator.createSetNextSeqId(payrollPeriods);
		} catch (GenericEntityException e1) {
			e1.printStackTrace();
		}
		return payrollPeriods;
	}

	public static String getYY(int year)
	{
		String yearAsString = String.valueOf(year);
		if (yearAsString.length() == 4)
			return yearAsString.substring(2, 4);
		
		return yearAsString;
	}	//	getYY

}
