package org.ofbiz.accounting.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.ofbiz.accountholdertransactions.AccHolderTransactionServices;

import org.ofbiz.accountholdertransactions.LoanUtilities;
import org.ofbiz.accounting.util.UtilAccounting;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactoryImpl;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.Writer;

import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.calendar.RecurrenceRule;
import org.ofbiz.webapp.event.EventHandlerException;

import java.io.IOException;

import org.ofbiz.service.GenericServiceException;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ofbiz.service.GenericDispatcherFactory;

import javolution.util.FastMap;

import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;

/***
 * @author Japheth Odonya
 *
 *         Purpose : Get SASRA Report Values
 * @author Japheth Odonya @when Feb 12, 2015 6:11:46 PM
 *
 * */
public class SasraReportsService {
	public static Logger log = Logger.getLogger(SasraReportsService.class);

	/***
	 * Get Totals given report ID, report Item Code and the from and thru dates
	 * */
	public static BigDecimal getReportItemTotals(Timestamp fromDate,
			Timestamp thruDate, String reportId, String reportItemCode) {

		// TODO Fix the code
		// Get Accounts List

		// Get SasraReportItem given reportId and reportItemCode
		String reportItemId = getReportItemId(reportId, reportItemCode);

		List<String> listAccountIds = getAccountIds(reportItemId);

		BigDecimal bdTotalAmount = BigDecimal.ZERO;

		bdTotalAmount = getAccountTotal(fromDate, thruDate, listAccountIds);

		return bdTotalAmount;
	}

	/**
	 * Returns Ratio
	 * */
	public static BigDecimal getReportItemRatio(Timestamp fromDate,
			Timestamp thruDate, String reportId,
			String reportItemCodeNumerator, String reportItemCodeDenominator) {

		// TODO Fix the code

		BigDecimal bdNumeratorTotal = getReportItemTotals(fromDate, thruDate,
				reportId, reportItemCodeNumerator);
		BigDecimal bdDenominatorTotal = getReportItemTotals(fromDate, thruDate,
				reportId, reportItemCodeDenominator);

		if ((bdNumeratorTotal.compareTo(BigDecimal.ZERO) == 0)
				|| (bdDenominatorTotal.compareTo(BigDecimal.ZERO) == 0))
			return BigDecimal.ZERO;

		return bdNumeratorTotal.divide(bdDenominatorTotal, 4,
				RoundingMode.HALF_UP);
	}

	/**
	 * Returns Percentage
	 * */
	public static BigDecimal getReportItemRatioPercentage(Timestamp fromDate,
			Timestamp thruDate, String reportId,
			String reportItemCodeNumerator, String reportItemCodeDenominator) {

		// TODO Fix the code return as a percentage * 100
		return getReportItemRatio(fromDate, thruDate, reportId,
				reportItemCodeNumerator, reportItemCodeDenominator).multiply(
				new BigDecimal(100));
	}

	/**
	 * Returns Difference between two Item Values
	 * */
	public static BigDecimal getReportItemDifference(Timestamp fromDate,
			Timestamp thruDate, String reportId, String reportItemCodeMinuend,
			String reportItemCodeSubtrahend) {

		// TODO Fix the code
		BigDecimal bdDifference = getReportItemTotals(fromDate, thruDate,
				reportId, reportItemCodeMinuend).subtract(
				getReportItemTotals(fromDate, thruDate, reportId,
						reportItemCodeSubtrahend));
		return bdDifference;
	}

	/**
	 * Returns of two Item Codes totals
	 * */
	public static BigDecimal getReportItemSum(Timestamp fromDate,
			Timestamp thruDate, String reportId,
			String reportItemCodeFirstAddend, String reportItemCodeSecondAddend) {

		BigDecimal bdSum = getReportItemTotals(fromDate, thruDate, reportId,
				reportItemCodeFirstAddend).add(
				getReportItemTotals(fromDate, thruDate, reportId,
						reportItemCodeSecondAddend));

		return bdSum;
	}

	/***
	 * Utility for getting Report Item Id given reportId and reportItemCode
	 * */
	private static String getReportItemId(String reportId, String reportItemCode) {
		EntityConditionList<EntityExpr> sasraReportItemConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"reportId", EntityOperator.EQUALS, reportId),
						EntityCondition.makeCondition("code",
								EntityOperator.EQUALS, reportItemCode)),
						EntityOperator.AND);

		List<GenericValue> sasraReportItemELI = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			sasraReportItemELI = delegator.findList("SasraReportItem",
					sasraReportItemConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		String reportItemId = null;
		for (GenericValue genericValue : sasraReportItemELI) {
			reportItemId = genericValue.getString("reportItemId");
		}
		return reportItemId;
	}

	/***
	 * Utility for getting list of accountIds for a given report Item Id
	 * */
	private static List<String> getAccountIds(String reportItemId) {
		List<String> listAccountIds = new ArrayList<String>();

		// Get the list of account ids that will be summed up for this
		// reportItem
		EntityConditionList<EntityExpr> sasraReportGlAccountConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"reportItemId", EntityOperator.EQUALS, reportItemId)),
						EntityOperator.AND);

		List<GenericValue> sasraReportGlAccountELI = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			sasraReportGlAccountELI = delegator.findList(
					"SasraReportGlAccount", sasraReportGlAccountConditions,
					null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		// String reportItemId = null;
		for (GenericValue genericValue : sasraReportGlAccountELI) {
			// reportItemId = genericValue.getString("reportItemId");
			// Add to list
			listAccountIds.add(genericValue.getString("glAccountId"));
		}

		return listAccountIds;
	}

	/***
	 * Computes the Total for the list of accounts provided
	 * */
	private static BigDecimal getAccountTotal(Timestamp fromDate,
			Timestamp thruDate, List<String> listAccountIds) {
		// TODO Auto-generated method stub
		BigDecimal bdTotal = BigDecimal.ZERO;
		for (String accountId : listAccountIds) {
			bdTotal = bdTotal
					.add(getAccountTotal(fromDate, thruDate, accountId));
		}
		return bdTotal;
	}

	/***
	 * Get total for a give account
	 * */
	private static BigDecimal getAccountTotal(Timestamp fromDate,
			Timestamp thruDate, String accountId) {
		// TODO Auto-generated method stub

		BigDecimal bdBalanceAmount = BigDecimal.ZERO;

		BigDecimal totalDebitsToOpeningDates = getDebitCreditTotalBalanceByDate(fromDate, "D", accountId);
		BigDecimal totalDebitsToEndingDates = getDebitCreditTotalBalanceByDate(thruDate, "D", accountId);

		BigDecimal totalCreditsToOpeningDates = getDebitCreditTotalBalanceByDate(fromDate, "C", accountId);
		BigDecimal totalCreditsToEndingDates = getDebitCreditTotalBalanceByDate(thruDate, "C", accountId);

		GenericValue glAccount = getGLAccount(accountId);

		Boolean isDebit = null;
		try {
			isDebit = UtilAccounting.isDebitAccount(glAccount);
		} catch (GenericEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (isDebit == null) {
			return BigDecimal.ZERO;
		}

		if (isDebit) {
			// return Debit Balance
			bdBalanceAmount = totalDebitsToEndingDates
					.subtract(totalCreditsToEndingDates);

		} else {
			// return Credit Balance
			bdBalanceAmount = totalCreditsToEndingDates
					.subtract(totalDebitsToEndingDates);
		}

		return bdBalanceAmount;
	}

	private static GenericValue getGLAccount(String accountId) {

		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		GenericValue glAccount = null;
		try {
			glAccount = delegator.findOne("GlAccount",
					UtilMisc.toMap("glAccountId", accountId), false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}
		return glAccount;
	}

	private static BigDecimal getDebitCreditTotalBalanceByDate(
			Timestamp transactionDate, String debitCredit, String accountId) {
		EntityConditionList<EntityExpr> acctgTransEntrySumsConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition
						.makeCondition("organizationPartyId",
								EntityOperator.EQUALS, "Company"),
						EntityCondition.makeCondition("glAccountId",
								EntityOperator.EQUALS, accountId)

						, EntityCondition.makeCondition("isPosted",
								EntityOperator.EQUALS, "Y")

						, EntityCondition.makeCondition("debitCreditFlag",
								EntityOperator.EQUALS, debitCredit)

						, EntityCondition.makeCondition("glFiscalTypeId",
								EntityOperator.EQUALS, "ACTUAL")

						, EntityCondition.makeCondition("transactionDate",
								EntityOperator.LESS_THAN, transactionDate)),
						EntityOperator.AND);

		List<GenericValue> acctgTransEntrySumsELI = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			acctgTransEntrySumsELI = delegator.findList(
					"AcctgTransEntrySumsWithTransType",
					acctgTransEntrySumsConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		// String reportItemId = null;
		BigDecimal bdAmount = BigDecimal.ZERO;
		for (GenericValue genericValue : acctgTransEntrySumsELI) {
			// reportItemId = genericValue.getString("reportItemId");

			bdAmount = bdAmount.add(genericValue.getBigDecimal("amount"));
		}
		return bdAmount;
	}

	public static Long getAccountTotalsCount(String classificationId,
			BigDecimal bdLower, BigDecimal bdUpper) {
		return getAccountTotals(classificationId, bdLower, bdUpper).getCount();
	}

	public static BigDecimal getAccountTotalsTotal(String classificationId,
			BigDecimal bdLower, BigDecimal bdUpper) {
		return getAccountTotals(classificationId, bdLower, bdUpper).getTotal();
	}

	/***
	 * Sum Member Accounts greater than Lower and Less than Upper
	 * **/
	public static AccountCount getAccountTotals(String classificationId,
			BigDecimal bdLower, BigDecimal bdUpper) {
		AccountCount accountAccount = new AccountCount();
		// BigDecimal bdTotal = BigDecimal.ZERO;
		accountAccount.setCount(0L);
		accountAccount.setTotal(BigDecimal.ZERO);

		// Get Accounts
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		GenericValue depositType = null;
		try {
			depositType = delegator.findOne("DepositType",
					UtilMisc.toMap("depositTypeId", classificationId), false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		// Get Accounts
		EntityConditionList<EntityExpr> depositTypeItemConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"depositTypeId", EntityOperator.EQUALS,
						classificationId)), EntityOperator.AND);
		List<GenericValue> depositTypeItemELI = null;
		try {
			depositTypeItemELI = delegator.findList("DepositTypeItem",
					depositTypeItemConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		Long accountProductId = null;
		for (GenericValue genericValue : depositTypeItemELI) {
			// reportItemId = genericValue.getString("reportItemId");
			accountProductId = genericValue.getLong("accountProductId");

			accountAccount = processMemberAccounts(accountAccount,
					accountProductId, bdLower, bdUpper);
			// Get Members with this account

			// each member , get account balance

			// check if balance within range and add total and increment count
		}

		// For each Account compute total balance and add it to total if within
		// range

		return accountAccount;
	}

	private static AccountCount processMemberAccounts(
			AccountCount accountAccount, Long accountProductId,
			BigDecimal bdLower, BigDecimal bdUpper) {
		// TODO Auto-generated method stub
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		// Get Accounts
		EntityConditionList<EntityExpr> memberAccountConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"accountProductId", EntityOperator.EQUALS,
						accountProductId)), EntityOperator.AND);
		List<GenericValue> memberAccountELI = null;
		try {
			memberAccountELI = delegator.findList("MemberAccount",
					memberAccountConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		Long memberAccountId = null;
		BigDecimal bdTotal = BigDecimal.ZERO;
		Long count = 0L;
		for (GenericValue genericValue : memberAccountELI) {
			// reportItemId = genericValue.getString("reportItemId");
			memberAccountId = genericValue.getLong("memberAccountId");

			// accountAccount = processMemberAccounts(accountAccount,
			// accountProductId);
			// Get Members with this account
			bdTotal = AccHolderTransactionServices.getBookBalanceVer3(
					memberAccountId.toString(), delegator);
			// each member , get account balance

			if ((bdTotal.compareTo(bdLower) > -1)
					&& (bdTotal.compareTo(bdUpper) < 1)) {
				accountAccount.setCount(accountAccount.getCount() + 1);
				accountAccount.setTotal(accountAccount.getTotal().add(bdTotal));
			}

			// check if balance within range and add total and increment count
		}

		return accountAccount;
	}

	/****
	 * Count Member Accounts greater than Lower and Less than Upper
	 * */
	public static Long getAccountCounts(String classificationId,
			BigDecimal bdLower, BigDecimal bdUpper) {
		Long count = 0L;
		// Get Accounts

		// For each Account compute total balance and add it to total if within
		// range

		return count;
	}

	
	public static Long getLoanCount(Long lowerDays, Long upperDays){
		
		Long noOfLoans = 0L;
		List<Long> listLoanApplicationIDs = getLoanApplicationIDs();
		Long noOfDaysNotPaid = 0L;
		for (Long loanApplicationId : listLoanApplicationIDs) {
			
			noOfDaysNotPaid = getNumberOfDaysNotPaid(loanApplicationId);
		}
		
		if (lowerDays == null){
			//We are dealing with zero
			
		} else if (upperDays == null){
			//We are dealing with upper limit - greater than 30
		} else{
			//Its normal - look at lower and upper
		}
		
		return noOfLoans;
	}
	
	private static Long getNumberOfDaysNotPaid(Long loanApplicationId) {
		// TODO Auto-generated method stub
		return null;
	}

	private static List<Long> getLoanApplicationIDs() {
		List<Long> listLoanApplicationIds = new ArrayList<Long>();
		List<GenericValue> loanApplicationELI = null;
		//GenericValue loanApplication = getLoanApplication(loanApplicationId);
		Long loanDisbursedStatusId = LoanUtilities.getLoanStatusId("DISBURSED");
		EntityConditionList<EntityExpr> loanApplicationConditions = EntityCondition
				.makeCondition(
						UtilMisc.toList(EntityCondition.makeCondition(
								"loanStatusId", EntityOperator.EQUALS,
								loanDisbursedStatusId)
								), EntityOperator.AND);
		
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		
		Set<String> setSelectField = new HashSet<String>(); 
		setSelectField.add("loanApplicationId");
		try {
			loanApplicationELI = delegator.findList("LoanApplication",
					loanApplicationConditions, setSelectField, null, null, false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}
		
		for (GenericValue genericValue : loanApplicationELI) {
			listLoanApplicationIds.add(genericValue.getLong("loanApplicationId"));
		}
		
		return listLoanApplicationIds;
	}

	public static BigDecimal getLoanBalanceTotals(Long lowerDays, Long upperDays){
		
		if (lowerDays == null){
			//We are dealing with zero
			
		} else if (upperDays == null){
			//We are dealing with upper limit - greater than 30
		} else{
			//Its normal - look at lower and upper

		}

		return BigDecimal.ZERO;
	}

	
	/***
	 * @author Samoei Phil
	 *
	 * Purpose : Get GL Accounts that only belong to a specific branch
	 * @when May 13, 2015 12:18:36 PM
	 *
	 * */
	public static String getBranchGlAccounts(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, Object> result = FastMap.newInstance();
		Delegator delegator = (Delegator) request.getAttribute("delegator");
		String branchId = (String) request.getParameter("branchId");
		log.info("Retrived Branch ID:>>>>>>>>>>>>>>> ############ " + branchId);
		List<GenericValue> branchGlAccounts = null;

		try {
			branchGlAccounts = delegator.findList(
					"GlAccountOrganizationAndClass", EntityCondition
							.makeCondition("organizationPartyId", branchId),
					null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		if (branchGlAccounts == null) {
			result.put("", "GL Accounts Have not been Set for this branch");
		} else {
			for (GenericValue genericValue : branchGlAccounts) {
				result.put(genericValue.get("accountCode").toString(), genericValue.get("accountName"));
			}
		}

		Gson gson = new Gson();
		String json = gson.toJson(result);

		// set the X-JSON content type
		response.setContentType("application/x-json");
		// jsonStr.length is not reliable for unicode characters
		try {
			response.setContentLength(json.getBytes("UTF8").length);
		} catch (UnsupportedEncodingException e) {
			try {
				throw new EventHandlerException("Problems with Json encoding",
						e);
			} catch (EventHandlerException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		// return the JSON String
		Writer out;
		try {
			out = response.getWriter();
			out.write(json);
			out.flush();
		} catch (IOException e) {
			try {
				throw new EventHandlerException(
						"Unable to get response writer", e);
			} catch (EventHandlerException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		return json;
	}

}
