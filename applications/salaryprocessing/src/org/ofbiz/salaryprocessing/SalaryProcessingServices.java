package org.ofbiz.salaryprocessing;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.ofbiz.accountholdertransactions.AccHolderTransactionServices;
import org.ofbiz.accountholdertransactions.LoanRepayments;
import org.ofbiz.accountholdertransactions.LoanUtilities;
import org.ofbiz.accountholdertransactions.RemittanceServices;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactoryImpl;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.transaction.GenericTransactionException;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.loans.LoanServices;
import org.ofbiz.loansprocessing.LoansProcessingServices;

public class SalaryProcessingServices {
	public static Logger log = Logger.getLogger(SalaryProcessingServices.class);

	// AccHolderTransactionServices.

	// public static synchronized String processSalaryReceivedNoDeduct(
	// HttpServletRequest request, HttpServletResponse response)
	public static synchronized String processSalaryReceivedNoDeduct(
			Long salaryMonthYearId, Map<String, String> userLogin) {

		// salaryMonthYearId
		// String salaryMonthYearId = (String) request
		// .getParameter("salaryMonthYearId");

		/***
		 * Get month year and employerCode from SalaryMonthYear where
		 * salaryMonthYearId is the given value
		 * */
		GenericValue salaryMonthYear = null;
		// salaryMonthYearId = salaryMonthYearId.replaceAll(",", "");
		Long salaryMonthIdLong = salaryMonthYearId;
		salaryMonthYear = LoanUtilities.getSalaryMonthYear(salaryMonthIdLong);

		String month = String.valueOf(salaryMonthYear.getLong("month"));
		String year = String.valueOf(salaryMonthYear.getLong("year"));
		String stationId = salaryMonthYear.getString("stationId");
		stationId = stationId.replaceAll(",", "");
		String employerCode = LoanUtilities.getStationEmployerCode(stationId);

		List<GenericValue> listMemberSalaryItems = getMemberSalaryList(month,
				year, employerCode, salaryMonthYearId);

		log.info("SSSSSSSSSSSS salaryMonthYearId SSSSSSSS ::: "
				+ salaryMonthYearId);

		if ((listMemberSalaryItems == null)
				|| (listMemberSalaryItems.size() < 1)) {
			return " No data to process or station already processed !";
		}

		// Cheque that the amount available is equal to the total not salary
		BigDecimal bdTotalNetSalaryAmt = getTotalNetSalaryAmount(salaryMonthYearId);
		BigDecimal bdTotalChequeAmountAvailable = RemittanceServices
				.getTotalRemittedChequeAmountAvailable(employerCode, month,
						year);

		// Everything to 2 decimal places
		bdTotalNetSalaryAmt = bdTotalNetSalaryAmt.setScale(2,
				RoundingMode.HALF_DOWN);
		bdTotalChequeAmountAvailable = bdTotalChequeAmountAvailable.setScale(2,
				RoundingMode.HALF_DOWN);

		if (bdTotalNetSalaryAmt.compareTo(bdTotalChequeAmountAvailable) != 0) {
			return "The available cheque amount must be equal to the salaries total, Total Salary Amount is "
					+ bdTotalNetSalaryAmt
					+ " while total cheque amounts is "
					+ bdTotalChequeAmountAvailable;
		}

		// Remove Current Logs first
		removeMissingPayrollNumbersLog(month, year, employerCode);
		log.info("NOOOOOO DEDUCT LLLLLLLLLLLLLLLL Month " + month + " Year "
				+ year + " Employer Code  " + employerCode);

		// List<GenericValue> MemberSalaryELI = null;

		Boolean missingPayrollNumbers = getMissingPayrollNumbers(month, year,
				employerCode, salaryMonthIdLong);

		if (missingPayrollNumbers) {

			log.info("MMMMMMMMMMMMMMM Missing Payroll Numbe, will exit LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);
			return "One or more payroll numbers missing in the system , please check the missing payroll numbers menu/link!";
		} else {
			log.info("EEEEEEEEEEEEEE Available Payroll Numbers, will continue LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);

		}

		// Check that all members have Savings account - code 999
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);
		Boolean missingSavingsAccount = false;
		String missingSavingsListing = "";
		// clearMissingMember
		RemittanceServices.clearMissingMember(month, employerCode);
		// RemittanceServices.re
		for (GenericValue memberSalary : listMemberSalary) {

			// Check if member has code 999 account
			// AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE
			if (!LoanUtilities.hasAccount(
					AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
					memberSalary.getString("payrollNumber"))) {
				missingSavingsAccount = true;

				if (missingSavingsListing.equals("")) {
					missingSavingsListing = memberSalary
							.getString("payrollNumber");
				} else {
					missingSavingsListing = missingSavingsListing + " , "
							+ memberSalary.getString("payrollNumber");
				}

				// Add User to missing accounts

				RemittanceServices.addMissingMemberLog(userLogin,
						memberSalary.getString("payrollNumber"), month,
						employerCode,
						AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
						null, null);
			}
		}

		if (missingSavingsAccount) {
			return "There are member accounts missing, please check the Missing Members Members menu in Account Holders transactions . The list has these payrolls ("
					+ missingSavingsListing + ")";
		}

		// Continue Processing for Salary Without Deductions

		// Get the Salary Processing Charge
		// Get the Salary Processing Charge Excise Duty

		// Get all the charges for transactiontype SALARYPROCESSING
		List<GenericValue> listAccountProductCharge = null;

		listAccountProductCharge = LoanUtilities.getAccountProductChargeList(
				"SALARYPROCESSING",
				AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((listAccountProductCharge == null)
				|| (listAccountProductCharge.size() < 2)) {
			return " Please check that Salary Processing Charge and its excise duty are defined with correct amount / figure for each!";
		}

		Long productChargeId = null;
		GenericValue productCharge = null;

		BigDecimal bdSalaryChargeAmt = BigDecimal.ZERO;
		BigDecimal bdSalaryExciseAmt = BigDecimal.ZERO;
		Long salaryProductChargeId = null;
		String salaryProductChargeName = null;
		Long salaryExciseDutyId = null;
		String salaryExciseDutyName = null;

		for (GenericValue genericValue : listAccountProductCharge) {
			productChargeId = genericValue.getLong("productChargeId");
			log.info(" CCCCCCCCCCCCCCC Charge ID "
					+ genericValue.getLong("productChargeId"));

			productCharge = LoanUtilities.getProductCharge(productChargeId);

			if (productCharge != null)
				log.info(" CCCCCCCCCCCCCCC Charge Name "
						+ productCharge.getString("name"));

			// if has no parent the its salary charge amount
			if (genericValue.getLong("parentChargeId") == null) {
				// Its salary amount
				bdSalaryChargeAmt = genericValue.getBigDecimal("fixedAmount");

				salaryProductChargeId = genericValue.getLong("productChargeId");
				salaryProductChargeName = productCharge.getString("name");
			}

			// if has parent then its excise duty amount
			if (genericValue.getLong("parentChargeId") != null) {
				// Its salary amount
				bdSalaryExciseAmt = genericValue.getBigDecimal("rateAmount")
						.multiply(bdSalaryChargeAmt)
						.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);

				salaryExciseDutyId = genericValue.getLong("productChargeId");
				salaryExciseDutyName = productCharge.getString("name");
			}

			log.info(" CCCCCCCCCCCCCCCSSSS Salary Charge " + bdSalaryChargeAmt);
			log.info(" CCCCCCCCCCCCCCCSSSS Excise Duty " + bdSalaryExciseAmt);
		}

		// Check that Salary Charge and Excise duty have account set
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		if ((salaryChargeCreditAccountId == null)
				|| (salaryChargeCreditAccountId.equals(""))) {
			return "Please ensure that the Salary Processing charge has a gl account set !! Check Product Charge list in loans if charge account is specified";
		}

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		if ((salaryExciseCreditAccountId == null)
				|| (salaryExciseCreditAccountId.equals(""))) {
			return "Please ensure that the Excise charge has a GL Account set !! Check Product Charge list in loans if charge account is specified";
		}

		// Employee Must have a branch
		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		if ((branchId == null) || (branchId.equals("")))
			return "The employee logged into the system must have a branch, please check with HR!!";

		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((savingsAccountGLAccountId == null)
				|| (savingsAccountGLAccountId.equals(""))) {
			return "Please ensure that the Savings Account (Code 999 ) has a ledger account defined in the setup";
		}
		String branchName = LoanUtilities.getBranchName(branchId);

		if (!LoanUtilities.organizationAccountMapped(savingsAccountGLAccountId,
				branchId)) {
			return "Please make sure that the Savings Account GL account is mapped to the employee's Branch ("
					+ branchName + ") ";
		}

		// Check that the accounts for Salary Processing Charge and Excise duty
		// are mapped to employee Branch
		if (!LoanUtilities.organizationAccountMapped(
				salaryChargeCreditAccountId, branchId)) {
			return "Please make sure that the Salary Charge Account is mapped to the employee branch ("
					+ branchName
					+ ") in the chart of accounts, consult FINANCE";
		}

		if (!LoanUtilities.organizationAccountMapped(
				salaryExciseCreditAccountId, branchId)) {
			return "Please make sure that the Excise Duty Account is mapped to the employee branch ("
					+ branchName
					+ ")  in the chart of accounts, consult FINANCE";
		}

		// Map<String, String> userLogin = (Map<String, String>) request
		// .getAttribute("userLogin");
		// For each Member
		// Post Net Salary
		// Post Salary Processing Charge
		// Post Salary Processing Excise Duty
		doProcessing(userLogin, month, year, employerCode, bdSalaryChargeAmt,
				bdSalaryExciseAmt, salaryProductChargeId,
				salaryProductChargeName, salaryExciseDutyId,
				salaryExciseDutyName, salaryMonthYearId);

		log.info("HHHHHHHHHHHH Salary Processing ... No Deductions !!!");

		// Writer out;
		// try {
		// out = response.getWriter();
		// out.write("");
		// out.flush();
		// } catch (IOException e) {
		// try {
		// throw new EventHandlerException(
		// "Unable to get response writer", e);
		// } catch (EventHandlerException e1) {
		// e1.printStackTrace();
		// }
		// }
		return "success";
	}

	private static void doProcessing(Map<String, String> userLogin,
			String month, String year, String employerCode,
			BigDecimal bdSalaryChargeAmt, BigDecimal bdSalaryExciseAmt,
			Long salaryProductChargeId, String salaryProductChargeName,
			Long salaryExciseDutyId, String salaryExciseDutyName,
			Long salaryMonthYearId) {
		// Get the payroll numbers from MemberSalary given month, year and
		// employerCode
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);

		BigDecimal bdTotalSalaryPosted = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryCharge = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryExciseDuty = BigDecimal.ZERO;
		BigDecimal bdNetSalaryAmt = BigDecimal.ZERO;
		Long memberAccountId = null;
		String payrollNumber = null;

		// Create One AcctgTrans
		GenericValue accountTransaction = null;
		String acctgTransId = AccHolderTransactionServices
				.creatAccountTransRecord(accountTransaction, userLogin);

		String accountTransactionParentId = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		List<GenericValue> listSalaryToUpdate = new ArrayList<GenericValue>();
		for (GenericValue genericValue : listMemberSalary) {
			bdNetSalaryAmt = genericValue.getBigDecimal("netSalary");
			bdTotalSalaryPosted = bdTotalSalaryPosted.add(bdNetSalaryAmt);

			accountTransactionParentId = AccHolderTransactionServices
					.getcreateAccountTransactionParentId(memberAccountId,
							userLogin);
			payrollNumber = genericValue.getString("payrollNumber");
			// memberAccountId = LoanUtilities.getS
			// Add Net Salary to the Savings Account
			memberAccountId = AccHolderTransactionServices
					.getMemberSavingsAccountId(payrollNumber);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdNetSalaryAmt, memberAccountId, userLogin,
					"SALARYPROCESSING", accountTransactionParentId, null,
					acctgTransId, null, null);

			// Deduct the Salary Charge
			// Add Salary Charge
			bdTotalSalaryCharge = bdTotalSalaryCharge.add(bdSalaryChargeAmt);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdSalaryChargeAmt, memberAccountId, userLogin,
					salaryProductChargeName, accountTransactionParentId,
					salaryProductChargeId.toString(), acctgTransId, null, null);
			// Add Excise Duty
			bdTotalSalaryExciseDuty = bdTotalSalaryExciseDuty
					.add(bdSalaryExciseAmt);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdSalaryExciseAmt, memberAccountId, userLogin,
					salaryExciseDutyName, accountTransactionParentId,
					salaryExciseDutyId.toString(), acctgTransId, null, null);

			genericValue.set("processed", "Y");
			// listSalaryToUpdate.add(genericValue);
			try {
				delegator.createOrStore(genericValue);
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Post Total Net Salary in GL bdTotalSalaryPosted

		// SALARYPROCESSING
		// STATIONACCOUNTPAYMENT
		// GenericValue accountHolderTransactionSetup =
		// AccHolderTransactionServices
		// .getAccountHolderTransactionSetup("SALARYPROCESSING");

		GenericValue accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("STATIONACCOUNTPAYMENT");

		String debitAccountIdt = accountHolderTransactionSetup
				.getString("cashAccountId");
		String stationDepositAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		// LoanUtilities.get
		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		// salaryProductChargeId = salaryProductChargeId.replaceAll(",", "");
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));

		Long entrySequence = 1L;
		// ------------------------
		// Debit Leaf Base with the total
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalSalaryPosted, acctgTransId, "D",
				stationDepositAccountId, entrySequence.toString(), branchId);

		BigDecimal bdTotalCharges = bdTotalSalaryCharge
				.add(bdTotalSalaryExciseDuty);
		BigDecimal bdTotalMemberDepositAmt = bdTotalSalaryPosted
				.subtract(bdTotalCharges);

		// Credit Member Deposits with (total net - (total charge + total excise
		// duty))
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalMemberDepositAmt, acctgTransId, "C",
				savingsAccountGLAccountId, entrySequence.toString(), branchId);
		// Credit Salary Charge with total salary charge
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalSalaryCharge, acctgTransId,
						"C", salaryChargeCreditAccountId,
						entrySequence.toString(), branchId);
		// Credit Excise Duty with total excise duty
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalSalaryExciseDuty,
						acctgTransId, "C", salaryExciseCreditAccountId,
						entrySequence.toString(), branchId);

		// Update the MemberSalary to processed

		// Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		// try {
		// delegator.storeAll(listSalaryToUpdate);
		// } catch (GenericEntityException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		// Prior Transaction when a cheque was paid
		// Debit Cash at Bank
		// Credit Leaf Base

	}

	private static List<GenericValue> getMemberSalaryList(String month,
			String year, String employerCode, Long salaryMonthYearId) {
		List<GenericValue> memberSalaryELI = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);

		// EntityConditionList<EntityExpr> memberSalaryConditions =
		// EntityCondition
		// .makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
		// "month", EntityOperator.EQUALS, month),
		//
		// EntityCondition.makeCondition("year", EntityOperator.EQUALS,
		// year),
		//
		// EntityCondition.makeCondition("employerCode",
		// EntityOperator.EQUALS, employerCode),
		// // processed
		// EntityCondition.makeCondition("processed",
		// EntityOperator.EQUALS, null)),
		// EntityOperator.AND);

		EntityConditionList<EntityExpr> memberSalaryConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"salaryMonthYearId", EntityOperator.EQUALS,
						salaryMonthYearId),

				// processed
						EntityCondition.makeCondition("processed",
								EntityOperator.EQUALS, "N")),
						EntityOperator.AND);

		// salaryMonthYearId

		try {
			memberSalaryELI = delegator.findList("MemberSalary",
					memberSalaryConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		return memberSalaryELI;
	}

	private static Boolean getMissingPayrollNumbers(String month, String year,
			String employerCode, Long salaryMonthYearId) {
		Boolean missing = false;

		// Get All the Payroll Numbers in the Member Salary given month, year
		// and employerCode
		List<GenericValue> memberSalaryELI = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);

		EntityConditionList<EntityExpr> memberSalaryConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"salaryMonthYearId", EntityOperator.EQUALS,
						salaryMonthYearId)

				// EntityCondition
				// .makeCondition("year", EntityOperator.EQUALS, year),
				//
				// EntityCondition.makeCondition("employerCode",
				// EntityOperator.EQUALS, employerCode)

						), EntityOperator.AND);

		try {
			memberSalaryELI = delegator.findList("MemberSalary",
					memberSalaryConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		GenericValue missingSalaryPayrollNumber = null;
		String payrollNumber = "";
		for (GenericValue genericValue : memberSalaryELI) {
			// check if this Payroll Number exists in Member
			// If does not exist set missing to true
			payrollNumber = genericValue.getString("payrollNumber").trim();

			Boolean payrollExists = LoanUtilities
					.payrollNumberExists(payrollNumber);

			if (!payrollExists) {
				missing = true;

				// Save the Payroll Number Missing
				// Long missingMemberLogId =
				// delegator.getNextSeqIdLong("MissingMemberLog", 1);
				missingSalaryPayrollNumber = delegator.makeValue(
						"MissingSalaryPayrollNumber", UtilMisc.toMap(
								"isActive", "Y", "createdBy", "admin",
								"employerCode", employerCode, "payrollNumber",
								payrollNumber, "year", year, "month", month));
				try {
					delegator.createOrStore(missingSalaryPayrollNumber);
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}

		}

		return missing;
	}
	
	/***
	 * Missing List
	 * */
	private static String getMissingPayrollNumbersString(String month, String year,
			String employerCode, Long salaryMonthYearId) {
		Boolean missing = false;

		// Get All the Payroll Numbers in the Member Salary given month, year
		// and employerCode
		List<GenericValue> memberSalaryELI = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);

		EntityConditionList<EntityExpr> memberSalaryConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"salaryMonthYearId", EntityOperator.EQUALS,
						salaryMonthYearId)

				// EntityCondition
				// .makeCondition("year", EntityOperator.EQUALS, year),
				//
				// EntityCondition.makeCondition("employerCode",
				// EntityOperator.EQUALS, employerCode)

						), EntityOperator.AND);

		try {
			memberSalaryELI = delegator.findList("MemberSalary",
					memberSalaryConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		GenericValue missingSalaryPayrollNumber = null;
		String payrollNumber = "";
		
		String theList = "";
		for (GenericValue genericValue : memberSalaryELI) {
			// check if this Payroll Number exists in Member
			// If does not exist set missing to true
			payrollNumber = genericValue.getString("payrollNumber");

			Boolean payrollExists = LoanUtilities
					.payrollNumberExists(payrollNumber);

			if (!payrollExists) {
				missing = true;
				theList = theList + payrollNumber+" , ";
				// Save the Payroll Number Missing
				// Long missingMemberLogId =
				// delegator.getNextSeqIdLong("MissingMemberLog", 1);
				missingSalaryPayrollNumber = delegator.makeValue(
						"MissingSalaryPayrollNumber", UtilMisc.toMap(
								"isActive", "Y", "createdBy", "admin",
								"employerCode", employerCode, "payrollNumber",
								payrollNumber, "year", year, "month", month));
				try {
					delegator.createOrStore(missingSalaryPayrollNumber);
				} catch (GenericEntityException e) {
					e.printStackTrace();
				}
			}

		}

		return theList;
	}
	/***
	 * Full deduction
	 * **/
	// processSalaryReceivedDeduct(HttpServletRequest request,
	// HttpServletResponse response)
	public static synchronized String processSalaryReceivedDeduct(
			Long salaryMonthYearId, Map<String, String> userLogin) {

		/***
		 * Get month year and employerCode from SalaryMonthYear where
		 * salaryMonthYearId is the given value
		 * */
		/***
		 * Get month year and employerCode from SalaryMonthYear where
		 * salaryMonthYearId is the given value
		 * */
		GenericValue salaryMonthYear = null;
		// salaryMonthYearId = salaryMonthYearId.replaceAll(",", "");
		Long salaryMonthIdLong = salaryMonthYearId;
		salaryMonthYear = LoanUtilities.getSalaryMonthYear(salaryMonthIdLong);

		String month = String.valueOf(salaryMonthYear.getLong("month"));
		String year = String.valueOf(salaryMonthYear.getLong("year"));
		String stationId = salaryMonthYear.getString("stationId");
		stationId = stationId.replaceAll(",", "");
		String employerCode = LoanUtilities.getStationEmployerCode(stationId);

		List<GenericValue> listMemberSalaryItems = getMemberSalaryList(month,
				year, employerCode, salaryMonthYearId);

		log.info("SSSSSSSSSSSS salaryMonthYearId SSSSSSSS ::: "
				+ salaryMonthYearId);

		if ((listMemberSalaryItems == null)
				|| (listMemberSalaryItems.size() < 1)) {
			return " No data to process or station already processed !";
		}

		// Cheque that the amount available is equal to the total not salary
		BigDecimal bdTotalNetSalaryAmt = getTotalNetSalaryAmount(salaryMonthYearId);
		BigDecimal bdTotalChequeAmountAvailable = RemittanceServices
				.getTotalRemittedChequeAmountAvailable(employerCode, month,
						year);

		// Everything to 2 decimal places
		bdTotalNetSalaryAmt = bdTotalNetSalaryAmt.setScale(2,
				RoundingMode.HALF_DOWN);
		bdTotalChequeAmountAvailable = bdTotalChequeAmountAvailable.setScale(2,
				RoundingMode.HALF_DOWN);

		if (bdTotalNetSalaryAmt.compareTo(bdTotalChequeAmountAvailable) != 0) {
			return "The available cheque amount must be equal to the salaries total, Total Salary Amount is "
					+ bdTotalNetSalaryAmt
					+ " while total cheque amounts is "
					+ bdTotalChequeAmountAvailable;
		}

		// Remove Current Logs first
		removeMissingPayrollNumbersLog(month, year, employerCode);
		log.info("NOOOOOO DEDUCT LLLLLLLLLLLLLLLL Month " + month + " Year "
				+ year + " Employer Code  " + employerCode);

		// List<GenericValue> MemberSalaryELI = null;

		Boolean missingPayrollNumbers = getMissingPayrollNumbers(month, year,
				employerCode, salaryMonthIdLong);
		
		

		if (missingPayrollNumbers) {
			String theMissing = getMissingPayrollNumbersString(month, year,
					employerCode, salaryMonthIdLong);

			log.info("MMMMMMMMMMMMMMM Missing Payroll Numbe, will exit LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);
			return "One or more payroll numbers missing in the system , please check the missing payroll numbers menu/link! list :: "+theMissing;
		} else {
			log.info("EEEEEEEEEEEEEE Available Payroll Numbers, will continue LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);

		}

		// Check that all members have Savings account - code 999
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);
		Boolean missingSavingsAccount = false;
		String missingSavingsListing = "";
		// clearMissingMember
		RemittanceServices.clearMissingMember(month, employerCode);
		// RemittanceServices.re
		for (GenericValue memberSalary : listMemberSalary) {

			// Check if member has code 999 account
			// AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE
			if (!LoanUtilities.hasAccount(
					AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
					memberSalary.getString("payrollNumber"))) {
				missingSavingsAccount = true;

				if (missingSavingsListing.equals("")) {
					missingSavingsListing = memberSalary
							.getString("payrollNumber");
				} else {
					missingSavingsListing = missingSavingsListing + " , "
							+ memberSalary.getString("payrollNumber");
				}

				// Add User to missing accounts

				RemittanceServices.addMissingMemberLog(userLogin,
						memberSalary.getString("payrollNumber"), month,
						employerCode,
						AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
						null, null);
			}
		}

		if (missingSavingsAccount) {
			return "There are member accounts missing, please check the Missing Members Members menu in Account Holders transactions . The list has these payrolls ("
					+ missingSavingsListing + ")";
		}

		// Continue Processing for Salary Without Deductions

		// Get the Salary Processing Charge
		// Get the Salary Processing Charge Excise Duty

		// Get all the charges for transactiontype SALARYPROCESSING
		List<GenericValue> listAccountProductCharge = null;

		listAccountProductCharge = LoanUtilities.getAccountProductChargeList(
				"SALARYPROCESSING",
				AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((listAccountProductCharge == null)
				|| (listAccountProductCharge.size() < 2)) {
			return " Please check that Salary Processing Charge and its excise duty are defined with correct amount / figure for each!";
		}

		Long productChargeId = null;
		GenericValue productCharge = null;

		BigDecimal bdSalaryChargeAmt = BigDecimal.ZERO;
		BigDecimal bdSalaryExciseAmt = BigDecimal.ZERO;
		Long salaryProductChargeId = null;
		String salaryProductChargeName = null;
		Long salaryExciseDutyId = null;
		String salaryExciseDutyName = null;

		for (GenericValue genericValue : listAccountProductCharge) {
			productChargeId = genericValue.getLong("productChargeId");
			log.info(" CCCCCCCCCCCCCCC Charge ID "
					+ genericValue.getLong("productChargeId"));

			productCharge = LoanUtilities.getProductCharge(productChargeId);

			if (productCharge != null)
				log.info(" CCCCCCCCCCCCCCC Charge Name "
						+ productCharge.getString("name"));

			// if has no parent the its salary charge amount
			if (genericValue.getLong("parentChargeId") == null) {
				// Its salary amount
				bdSalaryChargeAmt = genericValue.getBigDecimal("fixedAmount");

				salaryProductChargeId = genericValue.getLong("productChargeId");
				salaryProductChargeName = productCharge.getString("name");
			}

			// if has parent then its excise duty amount
			if (genericValue.getLong("parentChargeId") != null) {
				// Its salary amount
				bdSalaryExciseAmt = genericValue.getBigDecimal("rateAmount")
						.multiply(bdSalaryChargeAmt)
						.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);

				salaryExciseDutyId = genericValue.getLong("productChargeId");
				salaryExciseDutyName = productCharge.getString("name");
			}

			log.info(" CCCCCCCCCCCCCCCSSSS Salary Charge " + bdSalaryChargeAmt);
			log.info(" CCCCCCCCCCCCCCCSSSS Excise Duty " + bdSalaryExciseAmt);
		}

		// Check that Salary Charge and Excise duty have account set
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		if ((salaryChargeCreditAccountId == null)
				|| (salaryChargeCreditAccountId.equals(""))) {
			return "Please ensure that the Salary Processing charge has a gl account set !! Check Product Charge list in loans if charge account is specified";
		}

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		if ((salaryExciseCreditAccountId == null)
				|| (salaryExciseCreditAccountId.equals(""))) {
			return "Please ensure that the Excise charge has a GL Account set !! Check Product Charge list in loans if charge account is specified";
		}

		// Employee Must have a branch
		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		if ((branchId == null) || (branchId.equals("")))
			return "The employee logged into the system must have a branch, please check with HR!!";

		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((savingsAccountGLAccountId == null)
				|| (savingsAccountGLAccountId.equals(""))) {
			return "Please ensure that the Savings Account (Code 999 ) has a ledger account defined in the setup";
		}
		String branchName = LoanUtilities.getBranchName(branchId);

		if (!LoanUtilities.organizationAccountMapped(savingsAccountGLAccountId,
				branchId)) {
			return "Please make sure that the Savings Account GL account is mapped to the employee's Branch ("
					+ branchName + ") ";
		}

		// Check that the accounts for Salary Processing Charge and Excise duty
		// are mapped to employee Branch
		if (!LoanUtilities.organizationAccountMapped(
				salaryChargeCreditAccountId, branchId)) {
			return "Please make sure that the Salary Charge Account is mapped to the employee branch ("
					+ branchName
					+ ") in the chart of accounts, consult FINANCE";
		}

		if (!LoanUtilities.organizationAccountMapped(
				salaryExciseCreditAccountId, branchId)) {
			return "Please make sure that the Excise Duty Account is mapped to the employee branch ("
					+ branchName
					+ ")  in the chart of accounts, consult FINANCE";
		}

		// For each Member
		// Post Net Salary
		// Post Salary Processing Charge
		// Post Salary Processing Excise Duty
		doProcessingWithDeductions(userLogin, month, year, employerCode,
				bdSalaryChargeAmt, bdSalaryExciseAmt, salaryProductChargeId,
				salaryProductChargeName, salaryExciseDutyId,
				salaryExciseDutyName, salaryMonthYearId);

		return "success";
	}

	/****
	 * @author Japheth Odonya @when Jun 17, 2015 11:39:29 PM
	 * 
	 *         Full Salary Deductions here
	 * */
	private static void doProcessingWithDeductions(
			Map<String, String> userLogin, String month, String year,
			String employerCode, BigDecimal bdSalaryChargeAmt,
			BigDecimal bdSalaryExciseAmt, Long salaryProductChargeId,
			String salaryProductChargeName, Long salaryExciseDutyId,
			String salaryExciseDutyName, Long salaryMonthYearId) {
		// Get the payroll numbers from MemberSalary given month, year and
		// employerCode
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);

		BigDecimal bdTotalSalaryPosted = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryCharge = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryExciseDuty = BigDecimal.ZERO;
		BigDecimal bdNetSalaryAmt = BigDecimal.ZERO;

		BigDecimal bdTotalPrincipalPaid = BigDecimal.ZERO;
		BigDecimal bdTotalInterestPaid = BigDecimal.ZERO;
		BigDecimal bdTotalInsurancePaid = BigDecimal.ZERO;

		Long memberAccountId = null;
		String payrollNumber = null;

		String accountTransactionParentId = null;

		List<GenericValue> listLoanRepayments = null;

		// Create One AcctgTrans
		GenericValue accountTransaction = null;
		String acctgTransId = AccHolderTransactionServices
				.creatAccountTransRecord(accountTransaction, userLogin);

		Set<Long> contributingProductIds = new HashSet<Long>();
		Map<Long, BigDecimal> productTotals = new HashMap<Long, BigDecimal>();

		for (GenericValue genericValue : listMemberSalary) {

			listLoanRepayments = new ArrayList<GenericValue>();

			// ###### Add the Net Salary to Member Account
			bdNetSalaryAmt = genericValue.getBigDecimal("netSalary");
			bdTotalSalaryPosted = bdTotalSalaryPosted.add(bdNetSalaryAmt);

			accountTransactionParentId = AccHolderTransactionServices
					.getcreateAccountTransactionParentId(memberAccountId,
							userLogin);
			payrollNumber = genericValue.getString("payrollNumber");
			// memberAccountId = LoanUtilities.getS
			// Add Net Salary to the Savings Account
			memberAccountId = AccHolderTransactionServices
					.getMemberSavingsAccountId(payrollNumber);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdNetSalaryAmt, memberAccountId, userLogin,
					"SALARYPROCESSING", accountTransactionParentId, null,
					acctgTransId, null, null);

			// ###### Deduct the Salary Processing Fee
			// Deduct the Salary Charge
			// Add Salary Charge
			bdTotalSalaryCharge = bdTotalSalaryCharge.add(bdSalaryChargeAmt);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdSalaryChargeAmt, memberAccountId, userLogin,
					salaryProductChargeName, accountTransactionParentId,
					salaryProductChargeId.toString(), acctgTransId, null, null);

			// ####### Deduct the Excise Duty
			// Deduct the Salary Charge
			// Add Salary Charge
			bdTotalSalaryExciseDuty = bdTotalSalaryExciseDuty
					.add(bdSalaryExciseAmt);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdSalaryExciseAmt, memberAccountId, userLogin,
					salaryExciseDutyName, accountTransactionParentId,
					salaryExciseDutyId.toString(), acctgTransId, null, null);

			// ####### Deduct the total Loan Deductions
			GenericValue member = RemittanceServices
					.getMemberByPayrollNo(payrollNumber);
			List<Long> listLoanApplicationIds = LoanServices
					.getDisbursedLoansIds(member.getLong("partyId"));
			//BigDecimal bdMemberTotalLoanExpectedAmt = BigDecimal.ZERO;
			BigDecimal bdMemberTotalDeductedAmount = BigDecimal.ZERO;
			BigDecimal bdLoanDeductedAmount = BigDecimal.ZERO;
			//BigDecimal bdLoanExpectedAmt = BigDecimal.ZERO;
			// String productName = "";
			GenericValue loanRepayment;
			Long loanRepaymentId = null;
			Delegator delegator = DelegatorFactoryImpl.getDelegator(null);

			BigDecimal bdSalaryBalance = bdNetSalaryAmt.subtract(
					bdSalaryChargeAmt).subtract(bdSalaryExciseAmt);
			
			for (Long loanApplicationId : listLoanApplicationIds) {
				BigDecimal totalLoanDue = LoanRepayments
						.getTotalLoanByLoanDue(loanApplicationId.toString());
				
				BigDecimal totalInsuranceDue = LoanRepayments
						.getTotalInsurancByLoanDue(loanApplicationId.toString());
				
				BigDecimal totalInterestDue = LoanRepayments
						.getTotalInterestByLoanDue(loanApplicationId.toString());
				
				BigDecimal totalPrincipalDue = LoanRepayments
						.getTotalPrincipaByLoanDue(loanApplicationId.toString());

				totalLoanDue = totalInterestDue.add(totalInsuranceDue).add(totalPrincipalDue);
				
				BigDecimal interestAmount = BigDecimal.ZERO;
				BigDecimal insuranceAmount = BigDecimal.ZERO;
				BigDecimal principalAmount = BigDecimal.ZERO;

				
				//Get the Insurance
				if (bdSalaryBalance.compareTo(totalInsuranceDue) > 0){
					insuranceAmount = totalInsuranceDue;
					bdSalaryBalance = bdSalaryBalance.subtract(insuranceAmount);
				} else {
					insuranceAmount = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				//If Insurance was overpaid , dont pay
				if (insuranceAmount.compareTo(BigDecimal.ZERO) == -1)
				{
					insuranceAmount = BigDecimal.ZERO;
				}
				
				
				//Get the Interest
				if (bdSalaryBalance.compareTo(totalInterestDue) > 0){
					interestAmount = totalInterestDue;
					bdSalaryBalance = bdSalaryBalance.subtract(interestAmount);
				} else {
					interestAmount = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				//If interest was overpaid , dont pay
				if (interestAmount.compareTo(BigDecimal.ZERO) == -1)
				{
					interestAmount = BigDecimal.ZERO;
				}
				
				
				//Get the Principal
				
				BigDecimal bdLoanBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
				if (totalPrincipalDue.compareTo(bdLoanBalance) > 0){
					principalAmount = bdLoanBalance;
				} else{
					principalAmount = totalPrincipalDue;
				}
				
				if (bdLoanBalance.compareTo(BigDecimal.ZERO) < 0){
					principalAmount = BigDecimal.ZERO;
				}
				
				if (bdSalaryBalance.compareTo(principalAmount) > 0){
					//principalAmount = principalAmount;
					bdSalaryBalance = bdSalaryBalance.subtract(principalAmount);
				} else {
					principalAmount = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				
				//Deducted will be Insurance + Interest + Principal
				bdLoanDeductedAmount = insuranceAmount.add(interestAmount).add(principalAmount);
				

				bdMemberTotalDeductedAmount = bdMemberTotalDeductedAmount
						.add(bdLoanDeductedAmount);
				
				if (bdLoanDeductedAmount.compareTo(BigDecimal.ZERO) < 1)
					continue;
				
				
				AccHolderTransactionServices.memberTransactionDeposit(
						bdLoanDeductedAmount, memberAccountId, userLogin,
						"LOANREPAYMENT", accountTransactionParentId, null,
						acctgTransId, null, loanApplicationId);

				
//				BigDecimal interestAmount = totalInterestDue;
//				BigDecimal insuranceAmount = totalInsuranceDue;
//				BigDecimal principalAmount = totalPrincipalDue;

				bdTotalPrincipalPaid = bdTotalPrincipalPaid
						.add(principalAmount);
				bdTotalInterestPaid = bdTotalInterestPaid.add(interestAmount);
				bdTotalInsurancePaid = bdTotalInsurancePaid
						.add(insuranceAmount);

				loanRepaymentId = delegator.getNextSeqIdLong("LoanRepayment");
				loanRepayment = delegator.makeValue("LoanRepayment", UtilMisc
						.toMap("loanRepaymentId", loanRepaymentId, "isActive",
								"Y",
								"createdBy",
								userLogin.get("userLoginId"),
								// "transactionType", "LOANREPAYMENT",
								"loanApplicationId", loanApplicationId,
								"partyId", member.getLong("partyId"),

								"transactionAmount", bdLoanDeductedAmount,

								"totalLoanDue", totalLoanDue,

								"totalInterestDue", totalInterestDue,

								"totalInsuranceDue", totalInsuranceDue,

								"totalPrincipalDue", totalPrincipalDue,

								"interestAmount", interestAmount,
								"insuranceAmount", insuranceAmount,
								"principalAmount", principalAmount,
								"repaymentMode", "SALARYPROCESSING",
								"month", month+year,
								"acctgTransId", acctgTransId

						));
				
				try {
					TransactionUtil.begin();
				} catch (GenericTransactionException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				try {
					delegator.createOrStore(loanRepayment);
				} catch (GenericEntityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					TransactionUtil.commit();
				} catch (GenericTransactionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// LoanRepayments.repayLoanWithoutDebitingCash(loanRepayment,
				// userLogin);
				// TODO use this to calcuate whether a loan can be paid
				// listLoanRepayments.add(loanRepayment);
				
				//If the loan balance after this is ZERO then clear / set loan as cleared
				BigDecimal newLoanBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
				if (newLoanBalance.compareTo(BigDecimal.ZERO) < 1){
					//Clear Loan
					LoanRepayments.clearLoan(loanApplicationId, userLogin, "CLeared by salary processing");
				}
			}

			// Process the loans

			// Deduct the total Account Contributions
			List<GenericValue> listMemberAccounts = LoanUtilities
					.getMemberContributingAccounts(member.getLong("partyId"));
			Long memberSavingsAccountId = LoanUtilities
					.getMemberAccountIdFromMemberAccount(
							member.getLong("partyId"),
							LoanUtilities
									.getAccountProductGivenCodeId(
											AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE)
									.getLong("accountProductId"));
			BigDecimal bdTotalContributingAmount = BigDecimal.ZERO;
			BigDecimal bdContributingAmt;
			for (GenericValue memberAccount : listMemberAccounts) {
				
				bdContributingAmt = LoanUtilities.getContributionAmount(
						memberAccount, member.getLong("partyId"));
				
				if (bdSalaryBalance.compareTo(bdContributingAmt) > 0){
					bdSalaryBalance = bdSalaryBalance.subtract(bdContributingAmt);
					
				} else{
					bdContributingAmt = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				if (bdContributingAmt.compareTo(BigDecimal.ZERO) > 0){ // Do this only if there is money in the salary to contribute. Dont touch the member's savings account

				contributingProductIds.add(memberAccount
						.getLong("accountProductId"));

				

				if (!productTotals.containsKey(memberAccount
						.getLong("accountProductId"))) {
					productTotals.put(
							memberAccount.getLong("accountProductId"),
							bdContributingAmt);
				} else {
					productTotals.put(
							memberAccount.getLong("accountProductId"),
							productTotals.get(
									memberAccount.getLong("accountProductId"))
									.add(bdContributingAmt));
				}

				// bdContributingAmt = LoanUtilities.getContributionAmount(
				// memberAccount, member.getLong("partyId"));
				// remove it from source account - the savings account
				
				
				
				AccHolderTransactionServices.memberTransactionDeposit(
						bdContributingAmt, memberSavingsAccountId, userLogin,
						"TOOTHERACCOUNTS", accountTransactionParentId, null,
						acctgTransId, memberAccount.getLong("accountProductId"), null);
				// Add to destination account e.g. member deposits
				AccHolderTransactionServices.memberTransactionDeposit(
						bdContributingAmt,
						memberAccount.getLong("memberAccountId"), userLogin,
						"DEPOSITFROMSALARY", accountTransactionParentId, null,
						acctgTransId, null, null);
				
				bdTotalContributingAmount = bdTotalContributingAmount
						.add(bdContributingAmt);
				
				} 
			}
			// Repay Loan with total loan repaid amount above for each loan
			// Make contribution to each account with the contribution amount

			// GL POsting
			// -- sum value for net salary - (salary processing fee + excise
			// duty + total loans + total account contributions)

			genericValue.set("processed", "Y");
			// listSalaryToUpdate.add(genericValue);
			try {
				delegator.createOrStore(genericValue);
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Post Total Net Salary in GL bdTotalSalaryPosted

		// SALARYPROCESSING
		// GenericValue accountHolderTransactionSetup =
		// AccHolderTransactionServices
		// .getAccountHolderTransactionSetup("SALARYPROCESSING");
		// String debitAccountId = accountHolderTransactionSetup
		// .getString("cashAccountId");
		// String creditAccountId = accountHolderTransactionSetup
		// .getString("memberDepositAccId");
		GenericValue accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("STATIONACCOUNTPAYMENT");

		String stationDepositAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");
		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		// salaryProductChargeId = salaryProductChargeId.replaceAll(",", "");
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		Long entrySequence = 1L;
		// ------------------------
		// Debit Leaf Base with the total
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalSalaryPosted, acctgTransId, "D",
				stationDepositAccountId, entrySequence.toString(), branchId);

		BigDecimal bdTotalCharges = bdTotalSalaryCharge
				.add(bdTotalSalaryExciseDuty);
		BigDecimal bdTotalMemberDepositAmt = bdTotalSalaryPosted
				.subtract(bdTotalCharges);
		

		// Credit Salary Charge with total salary charge
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalSalaryCharge, acctgTransId,
						"C", salaryChargeCreditAccountId,
						entrySequence.toString(), branchId);
		// Credit Excise Duty with total excise duty
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalSalaryExciseDuty,
						acctgTransId, "C", salaryExciseCreditAccountId,
						entrySequence.toString(), branchId);

		// Post the Loan Repayments
		// DR the savings withdrawable savingsAccountGLAccountId

//		entrySequence = entrySequence + 1;
//		AccHolderTransactionServices.createAccountPostingEntry(
//				bdTotalLoanAmountPaid, acctgTransId, "D",
//				savingsAccountGLAccountId, entrySequence.toString(), branchId);

		// Principal Payment
		// CR Loan Receivable bdTotalPrincipalPaid
		entrySequence = entrySequence + 1;

		accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("PRINCIPALPAYMENT");
		String loanReceivableAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalPrincipalPaid, acctgTransId, "C",
				loanReceivableAccountId, entrySequence.toString(), branchId);

		// Interest Payment
		// CR Interest Recevable bdTotalInterestPaid
		entrySequence = entrySequence + 1;

		accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("INTERESTPAYMENT");
		String interestReceivableAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalInterestPaid, acctgTransId,
						"C", interestReceivableAccountId,
						entrySequence.toString(), branchId);

		// Insurance Payment
		// CR Charge/Insurance Receivable bdTotalInsurancePaid
		entrySequence = entrySequence + 1;

		accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("INSURANCEPAYMENT");
		String insurancePaymentAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalInsurancePaid, acctgTransId, "C",
				insurancePaymentAccountId, entrySequence.toString(), branchId);

		// DR
		// TODO May use it to check loan Repayment
		// for (GenericValue genericValue : listLoanRepayments) {
		// //entrySequence = entrySequence + 1;
		// LoanRepayments.repayLoanWithoutDebitingCash(genericValue, userLogin,
		// entrySequence, acctgTransId);
		// }

		// listLoanRepayments.add(loanRepayment);

		// Update the MemberSalary to processed

		// Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		// try {
		// delegator.storeAll(listSalaryToUpdate);
		// } catch (GenericEntityException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		// Prior Transaction when a cheque was paid
		// Debit Cash at Bank
		// Credit Leaf Base

		// Posting the Account Products
		// Posting Contributed Accounts Member Deposits, Share Capital etc
		// Get the toal Account Amounts Contributed
		BigDecimal bdTotalAmountContributedToAccounts = BigDecimal.ZERO;

		String productglAccountId = "";

		for (Long accountProductId : contributingProductIds) {

			// Credit each account product there after

			entrySequence = entrySequence + 1;
			productglAccountId = LoanUtilities
					.getGLAccountIDForAccountProduct(LoanUtilities
							.getAccountProduct(accountProductId)
							.getString("code").trim());
			AccHolderTransactionServices.createAccountPostingEntry(
					productTotals.get(accountProductId), acctgTransId, "C",
					productglAccountId, entrySequence.toString(), branchId);

			bdTotalAmountContributedToAccounts = bdTotalAmountContributedToAccounts
					.add(productTotals.get(accountProductId));

		}

		// Debit savings with total account contributions
//		entrySequence = entrySequence + 1;
//		AccHolderTransactionServices.createAccountPostingEntry(
//				bdTotalAmountContributedToAccounts, acctgTransId, "D",
//				savingsAccountGLAccountId, entrySequence.toString(), branchId);
		
		BigDecimal bdTotalLoanAmountPaid = bdTotalPrincipalPaid.add(
				bdTotalInterestPaid).add(bdTotalInsurancePaid);
		bdTotalMemberDepositAmt = bdTotalMemberDepositAmt.subtract(bdTotalLoanAmountPaid);
		bdTotalMemberDepositAmt = bdTotalMemberDepositAmt.subtract(bdTotalAmountContributedToAccounts);

		// Credit Member Deposits with (total net - (total charge + total excise
		// duty))
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalMemberDepositAmt, acctgTransId, "C",
				savingsAccountGLAccountId, entrySequence.toString(), branchId);

	}

	public static BigDecimal getTotalNetSalaryAmount(Long salaryMonthYearId) {
		BigDecimal bdTotalAmount = BigDecimal.ZERO;

		// Need month, year and employerCode
		Long month = null;
		Long year = null;
		String employerCode = "";

		// Find the SalaryMonthYear
		GenericValue salaryMonthYear = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			salaryMonthYear = delegator.findOne("SalaryMonthYear",
					UtilMisc.toMap("salaryMonthYearId", salaryMonthYearId),
					false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		month = salaryMonthYear.getLong("month");
		year = salaryMonthYear.getLong("year");

		GenericValue station = LoanUtilities.getStation(salaryMonthYear
				.getString("stationId"));
		employerCode = station.getString("employerCode");

		// Get total amount
		List<GenericValue> StationSalarySumsELI = new ArrayList<GenericValue>();
		// Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		EntityConditionList<EntityExpr> StationSalarySumsConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"month", EntityOperator.EQUALS, month.toString()),
						EntityCondition.makeCondition("year",
								EntityOperator.EQUALS, year.toString()),
						
						EntityCondition.makeCondition("salaryMonthYearId",
										EntityOperator.EQUALS, salaryMonthYearId),

						EntityCondition.makeCondition("employerCode",
								EntityOperator.EQUALS, employerCode)),
						EntityOperator.AND);
		try {
			StationSalarySumsELI = delegator.findList("StationSalarySums",
					StationSalarySumsConditions, null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		for (GenericValue genericValue : StationSalarySumsELI) {
			bdTotalAmount = bdTotalAmount.add(genericValue
					.getBigDecimal("netSalary"));
		}

		return bdTotalAmount;
	}

	private static void removeMissingPayrollNumbersLog(String month,
			String year, String employerCode) {
		List<GenericValue> missingSalaryPayrollNumberELI = new ArrayList<GenericValue>();

		EntityConditionList<EntityExpr> missingSalaryPayrollNumberConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"employerCode", EntityOperator.EQUALS,
						employerCode.trim()),

				EntityCondition.makeCondition("month", EntityOperator.EQUALS,
						month.trim()),

				EntityCondition.makeCondition("year", EntityOperator.EQUALS,
						year.trim())

				), EntityOperator.AND);

		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			missingSalaryPayrollNumberELI = delegator.findList(
					"MissingSalaryPayrollNumber",
					missingSalaryPayrollNumberConditions, null, null, null,
					false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		try {
			delegator.removeAll(missingSalaryPayrollNumberELI);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

	}

	public static Long countMissingPayrollNumbers(String month, String year,
			String employerCode) {
		List<GenericValue> missingSalaryPayrollNumberELI = new ArrayList<GenericValue>();

		EntityConditionList<EntityExpr> missingSalaryPayrollNumberConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"employerCode", EntityOperator.EQUALS,
						employerCode.trim()),

				EntityCondition.makeCondition("month", EntityOperator.EQUALS,
						month.trim()),

				EntityCondition.makeCondition("year", EntityOperator.EQUALS,
						year.trim())

				), EntityOperator.AND);

		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			missingSalaryPayrollNumberELI = delegator.findList(
					"MissingSalaryPayrollNumber",
					missingSalaryPayrollNumberConditions, null, null, null,
					false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		return Long.valueOf(missingSalaryPayrollNumberELI.size());

	}

	/****
	 * Take the path to csv and salaryMonthYearId and do the processing of the
	 * csv
	 * */
	public static void processCSV(String csvPath, String salaryMonthYearIdStr) {

		log.info(" GGGGGGGGGGGGGGGGGGG ");
		log.info(" CSV Path (absolute is ) :::  " + csvPath);
		log.info(" Salary Month Year ID is :::  ) " + salaryMonthYearIdStr);

		/***
		 * Create MemberSalary
		 * 
		 * 
		 * <field name="memberSalaryId" type="id-vlong-int"></field> <field
		 * name="salaryMonthYearId" type="id-vlong-int"></field> <field
		 * name="isActive" type="indicator"></field> <field name="createdBy"
		 * type="id"></field> <field name="month" type="id"></field> <field
		 * name="year" type="id"></field> <field name="employerCode"
		 * type="id"></field> <field name="payrollNumber" type="id"></field>
		 * <field name="netSalary" type="fixed-point"></field> <field
		 * name="processed" type="indicator"></field>
		 * 
		 * */

		// String month = "";
		// String year = "";
		// String employerCode = "";
		Long salaryMonthYearIdLong = Long.valueOf(salaryMonthYearIdStr);
		// Need month, year and employerCode

		// Find the SalaryMonthYear
		GenericValue salaryMonthYear = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			salaryMonthYear = delegator.findOne("SalaryMonthYear",
					UtilMisc.toMap("salaryMonthYearId", salaryMonthYearIdLong),
					false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		Long month = salaryMonthYear.getLong("month");
		Long year = salaryMonthYear.getLong("year");

		GenericValue station = LoanUtilities.getStation(salaryMonthYear
				.getString("stationId"));
		String employerCode = station.getString("employerCode");

		BufferedReader br = null;
		String line = "";
		String csvSplitBy = ",";

		Long memberSalaryId;
		GenericValue memberSalary;

		List<GenericValue> listMemberSalary = new ArrayList<GenericValue>();

		// Add the records to Member Salaries
		int count = 0;
		BigDecimal netSalary = BigDecimal.ZERO;
		try {
			br = new BufferedReader(new FileReader(csvPath));

			while ((line = br.readLine()) != null) {
				String[] salary = line.split(csvSplitBy);
				count++;

				System.out.println(" Count " + count + " Payroll No "
						+ salary[0] + " Net Pay " + salary[1]);
				netSalary = new BigDecimal(salary[1]);
				memberSalaryId = delegator.getNextSeqIdLong("MemberSalary");
				memberSalary = delegator.makeValue("MemberSalary", UtilMisc
						.toMap("memberSalaryId", memberSalaryId,
								"salaryMonthYearId", salaryMonthYearIdLong,
								"isActive", "Y", "createdBy", "admin",
								// "transactionType", "LOANREPAYMENT",
								"month", month.toString(), "year",
								year.toString(),

								"employerCode", employerCode,

								"payrollNumber", salary[0].trim(),

								"netSalary", netSalary,

								"processed", "N"));

				listMemberSalary.add(memberSalary);
			}

			try {
				delegator.storeAll(listMemberSalary);
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {

			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

	private static GenericValue getSalaryMonthYear(Long salaryMonthYearIdLong) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @author Japheth Odonya @when May 8, 2015 3:50:07 PM
	 * 
	 *         processSalaryReceivedLoanDeductionOnly
	 * 
	 * */
	// (HttpServletRequest request, HttpServletResponse response)
	public static synchronized String processSalaryReceivedLoanDeductionOnly(
			Long salaryMonthYearId, Map<String, String> userLogin) {

		// salaryMonthYearId
		// String salaryMonthYearId = (String) request
		// .getParameter("salaryMonthYearId");

		/***
		 * Get month year and employerCode from SalaryMonthYear where
		 * salaryMonthYearId is the given value
		 * */
		GenericValue salaryMonthYear = null;
		// salaryMonthYearId = salaryMonthYearId.replaceAll(",", "");
		Long salaryMonthIdLong = salaryMonthYearId;
		salaryMonthYear = LoanUtilities.getSalaryMonthYear(salaryMonthIdLong);

		String month = String.valueOf(salaryMonthYear.getLong("month"));
		String year = String.valueOf(salaryMonthYear.getLong("year"));
		String stationId = salaryMonthYear.getString("stationId");
		stationId = stationId.replaceAll(",", "");
		String employerCode = LoanUtilities.getStationEmployerCode(stationId);

		List<GenericValue> listMemberSalaryItems = getMemberSalaryList(month,
				year, employerCode, salaryMonthYearId);

		log.info("SSSSSSSSSSSS salaryMonthYearId SSSSSSSS ::: "
				+ salaryMonthYearId);

		if ((listMemberSalaryItems == null)
				|| (listMemberSalaryItems.size() < 1)) {
			return " No data to process or station already processed !";
		}

		// Cheque that the amount available is equal to the total not salary
		BigDecimal bdTotalNetSalaryAmt = getTotalNetSalaryAmount(salaryMonthYearId);
		BigDecimal bdTotalChequeAmountAvailable = RemittanceServices
				.getTotalRemittedChequeAmountAvailable(employerCode, month,
						year);

		// Everything to 2 decimal places
		bdTotalNetSalaryAmt = bdTotalNetSalaryAmt.setScale(2,
				RoundingMode.HALF_DOWN);
		bdTotalChequeAmountAvailable = bdTotalChequeAmountAvailable.setScale(2,
				RoundingMode.HALF_DOWN);

		if (bdTotalNetSalaryAmt.compareTo(bdTotalChequeAmountAvailable) != 0) {
			return "The available cheque amount must be equal to the salaries total, Total Salary Amount is "
					+ bdTotalNetSalaryAmt
					+ " while total cheque amounts is "
					+ bdTotalChequeAmountAvailable;
		}

		// Remove Current Logs first
		removeMissingPayrollNumbersLog(month, year, employerCode);
		log.info("NOOOOOO DEDUCT LLLLLLLLLLLLLLLL Month " + month + " Year "
				+ year + " Employer Code  " + employerCode);

		// List<GenericValue> MemberSalaryELI = null;

		Boolean missingPayrollNumbers = getMissingPayrollNumbers(month, year,
				employerCode, salaryMonthIdLong);

		if (missingPayrollNumbers) {

			log.info("MMMMMMMMMMMMMMM Missing Payroll Numbe, will exit LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);
			
			String missingNumbers = getMissingPayrollNumbersString(month, year, employerCode, salaryMonthYearId);
			return "One or more payroll numbers missing in the system , please check the missing payroll numbers menu/link! Payroll Numbers"+missingNumbers;
		} else {
			log.info("EEEEEEEEEEEEEE Available Payroll Numbers, will continue LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);

		}

		// Check that all members have Savings account - code 999
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);
		Boolean missingSavingsAccount = false;
		String missingSavingsListing = "";
		// clearMissingMember
		RemittanceServices.clearMissingMember(month, employerCode);
		// RemittanceServices.re
		for (GenericValue memberSalary : listMemberSalary) {

			// Check if member has code 999 account
			// AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE
			if (!LoanUtilities.hasAccount(
					AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
					memberSalary.getString("payrollNumber"))) {
				missingSavingsAccount = true;

				if (missingSavingsListing.equals("")) {
					missingSavingsListing = memberSalary
							.getString("payrollNumber");
				} else {
					missingSavingsListing = missingSavingsListing + " , "
							+ memberSalary.getString("payrollNumber");
				}

				// Add User to missing accounts

				RemittanceServices.addMissingMemberLog(userLogin,
						memberSalary.getString("payrollNumber"), month,
						employerCode,
						AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
						null, null);
			}
		}

		if (missingSavingsAccount) {
			return "There are member accounts missing, please check the Missing Members Members menu in Account Holders transactions . The list has these payrolls ("
					+ missingSavingsListing + ")";
		}

		// Continue Processing for Salary Without Deductions

		// Get the Salary Processing Charge
		// Get the Salary Processing Charge Excise Duty

		// Get all the charges for transactiontype SALARYPROCESSING
		List<GenericValue> listAccountProductCharge = null;

		listAccountProductCharge = LoanUtilities.getAccountProductChargeList(
				"SALARYPROCESSING",
				AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((listAccountProductCharge == null)
				|| (listAccountProductCharge.size() < 2)) {
			return " Please check that Salary Processing Charge and its excise duty are defined with correct amount / figure for each!";
		}

		Long productChargeId = null;
		GenericValue productCharge = null;

		BigDecimal bdSalaryChargeAmt = BigDecimal.ZERO;
		BigDecimal bdSalaryExciseAmt = BigDecimal.ZERO;
		Long salaryProductChargeId = null;
		String salaryProductChargeName = null;
		Long salaryExciseDutyId = null;
		String salaryExciseDutyName = null;

		for (GenericValue genericValue : listAccountProductCharge) {
			productChargeId = genericValue.getLong("productChargeId");
			log.info(" CCCCCCCCCCCCCCC Charge ID "
					+ genericValue.getLong("productChargeId"));

			productCharge = LoanUtilities.getProductCharge(productChargeId);

			if (productCharge != null)
				log.info(" CCCCCCCCCCCCCCC Charge Name "
						+ productCharge.getString("name"));

			// if has no parent the its salary charge amount
			if (genericValue.getLong("parentChargeId") == null) {
				// Its salary amount
				bdSalaryChargeAmt = genericValue.getBigDecimal("fixedAmount");

				salaryProductChargeId = genericValue.getLong("productChargeId");
				salaryProductChargeName = productCharge.getString("name");
			}

			// if has parent then its excise duty amount
			if (genericValue.getLong("parentChargeId") != null) {
				// Its salary amount
				bdSalaryExciseAmt = genericValue.getBigDecimal("rateAmount")
						.multiply(bdSalaryChargeAmt)
						.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);

				salaryExciseDutyId = genericValue.getLong("productChargeId");
				salaryExciseDutyName = productCharge.getString("name");
			}

			log.info(" CCCCCCCCCCCCCCCSSSS Salary Charge " + bdSalaryChargeAmt);
			log.info(" CCCCCCCCCCCCCCCSSSS Excise Duty " + bdSalaryExciseAmt);
		}

		// Check that Salary Charge and Excise duty have account set
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		if ((salaryChargeCreditAccountId == null)
				|| (salaryChargeCreditAccountId.equals(""))) {
			return "Please ensure that the Salary Processing charge has a gl account set !! Check Product Charge list in loans if charge account is specified";
		}

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		if ((salaryExciseCreditAccountId == null)
				|| (salaryExciseCreditAccountId.equals(""))) {
			return "Please ensure that the Excise charge has a GL Account set !! Check Product Charge list in loans if charge account is specified";
		}

		// Employee Must have a branch
		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		if ((branchId == null) || (branchId.equals("")))
			return "The employee logged into the system must have a branch, please check with HR!!";

		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((savingsAccountGLAccountId == null)
				|| (savingsAccountGLAccountId.equals(""))) {
			return "Please ensure that the Savings Account (Code 999 ) has a ledger account defined in the setup";
		}
		String branchName = LoanUtilities.getBranchName(branchId);

		if (!LoanUtilities.organizationAccountMapped(savingsAccountGLAccountId,
				branchId)) {
			return "Please make sure that the Savings Account GL account is mapped to the employee's Branch ("
					+ branchName + ") ";
		}

		// Check that the accounts for Salary Processing Charge and Excise duty
		// are mapped to employee Branch
		if (!LoanUtilities.organizationAccountMapped(
				salaryChargeCreditAccountId, branchId)) {
			return "Please make sure that the Salary Charge Account is mapped to the employee branch ("
					+ branchName
					+ ") in the chart of accounts, consult FINANCE";
		}

		if (!LoanUtilities.organizationAccountMapped(
				salaryExciseCreditAccountId, branchId)) {
			return "Please make sure that the Excise Duty Account is mapped to the employee branch ("
					+ branchName
					+ ")  in the chart of accounts, consult FINANCE";
		}

		doProcessingLoanOnlyDeductions(userLogin, month, year, employerCode,
				bdSalaryChargeAmt, bdSalaryExciseAmt, salaryProductChargeId,
				salaryProductChargeName, salaryExciseDutyId,
				salaryExciseDutyName, salaryMonthYearId);
		// TODO
		// This will be implemented as part of Remittance (C7) processing !
		return "success";
	}

	/**
	 * @author Japheth Odonya @when May 8, 2015 3:52:59 PM
	 * 
	 *         processSalaryReceivedAccountContributionOnly
	 * 
	 * */
	// HttpServletRequest request, HttpServletResponse response
	public static synchronized String processSalaryReceivedAccountContributionOnly(
			Long salaryMonthYearId, Map<String, String> userLogin) {

		GenericValue salaryMonthYear = null;
		// salaryMonthYearId = salaryMonthYearId.replaceAll(",", "");
		Long salaryMonthIdLong = salaryMonthYearId;
		salaryMonthYear = LoanUtilities.getSalaryMonthYear(salaryMonthIdLong);

		String month = String.valueOf(salaryMonthYear.getLong("month"));
		String year = String.valueOf(salaryMonthYear.getLong("year"));
		String stationId = salaryMonthYear.getString("stationId");
		stationId = stationId.replaceAll(",", "");
		String employerCode = LoanUtilities.getStationEmployerCode(stationId);

		List<GenericValue> listMemberSalaryItems = getMemberSalaryList(month,
				year, employerCode, salaryMonthYearId);

		log.info("SSSSSSSSSSSS salaryMonthYearId SSSSSSSS ::: "
				+ salaryMonthYearId);

		if ((listMemberSalaryItems == null)
				|| (listMemberSalaryItems.size() < 1)) {
			return " No data to process or station already processed !";
		}

		// Cheque that the amount available is equal to the total not salary
		BigDecimal bdTotalNetSalaryAmt = getTotalNetSalaryAmount(salaryMonthYearId);
		BigDecimal bdTotalChequeAmountAvailable = RemittanceServices
				.getTotalRemittedChequeAmountAvailable(employerCode, month,
						year);

		// Everything to 2 decimal places
		bdTotalNetSalaryAmt = bdTotalNetSalaryAmt.setScale(2,
				RoundingMode.HALF_DOWN);
		bdTotalChequeAmountAvailable = bdTotalChequeAmountAvailable.setScale(2,
				RoundingMode.HALF_DOWN);

		if (bdTotalNetSalaryAmt.compareTo(bdTotalChequeAmountAvailable) != 0) {
			return "The available cheque amount must be equal to the salaries total, Total Salary Amount is "
					+ bdTotalNetSalaryAmt
					+ " while total cheque amounts is "
					+ bdTotalChequeAmountAvailable;
		}

		// Remove Current Logs first
		removeMissingPayrollNumbersLog(month, year, employerCode);
		log.info("NOOOOOO DEDUCT LLLLLLLLLLLLLLLL Month " + month + " Year "
				+ year + " Employer Code  " + employerCode);

		// List<GenericValue> MemberSalaryELI = null;

		Boolean missingPayrollNumbers = getMissingPayrollNumbers(month, year,
				employerCode, salaryMonthIdLong);

		if (missingPayrollNumbers) {

			log.info("MMMMMMMMMMMMMMM Missing Payroll Numbe, will exit LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);
			return "One or more payroll numbers missing in the system , please check the missing payroll numbers menu/link!";
		} else {
			log.info("EEEEEEEEEEEEEE Available Payroll Numbers, will continue LLLLLLLLLLLLLLLL Month "
					+ month
					+ " Year "
					+ year
					+ " Employer Code  "
					+ employerCode);

		}

		// Check that all members have Savings account - code 999
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);
		Boolean missingSavingsAccount = false;
		String missingSavingsListing = "";
		// clearMissingMember
		RemittanceServices.clearMissingMember(month, employerCode);
		// RemittanceServices.re
		for (GenericValue memberSalary : listMemberSalary) {

			// Check if member has code 999 account
			// AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE
			if (!LoanUtilities.hasAccount(
					AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
					memberSalary.getString("payrollNumber"))) {
				missingSavingsAccount = true;

				if (missingSavingsListing.equals("")) {
					missingSavingsListing = memberSalary
							.getString("payrollNumber");
				} else {
					missingSavingsListing = missingSavingsListing + " , "
							+ memberSalary.getString("payrollNumber");
				}

				// Add User to missing accounts

				RemittanceServices.addMissingMemberLog(userLogin,
						memberSalary.getString("payrollNumber"), month,
						employerCode,
						AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE,
						null, null);
			}
		}

		if (missingSavingsAccount) {
			return "There are member accounts missing, please check the Missing Members Members menu in Account Holders transactions . The list has these payrolls ("
					+ missingSavingsListing + ")";
		}

		// Continue Processing for Salary Without Deductions

		// Get the Salary Processing Charge
		// Get the Salary Processing Charge Excise Duty

		// Get all the charges for transactiontype SALARYPROCESSING
		List<GenericValue> listAccountProductCharge = null;

		listAccountProductCharge = LoanUtilities.getAccountProductChargeList(
				"SALARYPROCESSING",
				AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((listAccountProductCharge == null)
				|| (listAccountProductCharge.size() < 2)) {
			return " Please check that Salary Processing Charge and its excise duty are defined with correct amount / figure for each!";
		}

		Long productChargeId = null;
		GenericValue productCharge = null;

		BigDecimal bdSalaryChargeAmt = BigDecimal.ZERO;
		BigDecimal bdSalaryExciseAmt = BigDecimal.ZERO;
		Long salaryProductChargeId = null;
		String salaryProductChargeName = null;
		Long salaryExciseDutyId = null;
		String salaryExciseDutyName = null;

		for (GenericValue genericValue : listAccountProductCharge) {
			productChargeId = genericValue.getLong("productChargeId");
			log.info(" CCCCCCCCCCCCCCC Charge ID "
					+ genericValue.getLong("productChargeId"));

			productCharge = LoanUtilities.getProductCharge(productChargeId);

			if (productCharge != null)
				log.info(" CCCCCCCCCCCCCCC Charge Name "
						+ productCharge.getString("name"));

			// if has no parent the its salary charge amount
			if (genericValue.getLong("parentChargeId") == null) {
				// Its salary amount
				bdSalaryChargeAmt = genericValue.getBigDecimal("fixedAmount");

				salaryProductChargeId = genericValue.getLong("productChargeId");
				salaryProductChargeName = productCharge.getString("name");
			}

			// if has parent then its excise duty amount
			if (genericValue.getLong("parentChargeId") != null) {
				// Its salary amount
				bdSalaryExciseAmt = genericValue.getBigDecimal("rateAmount")
						.multiply(bdSalaryChargeAmt)
						.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);

				salaryExciseDutyId = genericValue.getLong("productChargeId");
				salaryExciseDutyName = productCharge.getString("name");
			}

			log.info(" CCCCCCCCCCCCCCCSSSS Salary Charge " + bdSalaryChargeAmt);
			log.info(" CCCCCCCCCCCCCCCSSSS Excise Duty " + bdSalaryExciseAmt);
		}

		// Check that Salary Charge and Excise duty have account set
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		if ((salaryChargeCreditAccountId == null)
				|| (salaryChargeCreditAccountId.equals(""))) {
			return "Please ensure that the Salary Processing charge has a gl account set !! Check Product Charge list in loans if charge account is specified";
		}

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		if ((salaryExciseCreditAccountId == null)
				|| (salaryExciseCreditAccountId.equals(""))) {
			return "Please ensure that the Excise charge has a GL Account set !! Check Product Charge list in loans if charge account is specified";
		}

		// Employee Must have a branch
		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		if ((branchId == null) || (branchId.equals("")))
			return "The employee logged into the system must have a branch, please check with HR!!";

		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		if ((savingsAccountGLAccountId == null)
				|| (savingsAccountGLAccountId.equals(""))) {
			return "Please ensure that the Savings Account (Code 999 ) has a ledger account defined in the setup";
		}
		String branchName = LoanUtilities.getBranchName(branchId);

		if (!LoanUtilities.organizationAccountMapped(savingsAccountGLAccountId,
				branchId)) {
			return "Please make sure that the Savings Account GL account is mapped to the employee's Branch ("
					+ branchName + ") ";
		}

		// Check that the accounts for Salary Processing Charge and Excise duty
		// are mapped to employee Branch
		if (!LoanUtilities.organizationAccountMapped(
				salaryChargeCreditAccountId, branchId)) {
			return "Please make sure that the Salary Charge Account is mapped to the employee branch ("
					+ branchName
					+ ") in the chart of accounts, consult FINANCE";
		}

		if (!LoanUtilities.organizationAccountMapped(
				salaryExciseCreditAccountId, branchId)) {
			return "Please make sure that the Excise Duty Account is mapped to the employee branch ("
					+ branchName
					+ ")  in the chart of accounts, consult FINANCE";
		}

		// TODO
		doProcessingAccountsOnlyDeductions(userLogin, month, year,
				employerCode, bdSalaryChargeAmt, bdSalaryExciseAmt,
				salaryProductChargeId, salaryProductChargeName,
				salaryExciseDutyId, salaryExciseDutyName, salaryMonthYearId);
		// "This will be implemented as part of Remittance (C7) processing !"
		return "success";
	}

	/***
	 * @author Japheth Odonya @when Jun 17, 2015 11:50:18 PM
	 *         doProcessingLoanOnlyDeductions
	 * */
	private static void doProcessingLoanOnlyDeductions(
			Map<String, String> userLogin, String month, String year,
			String employerCode, BigDecimal bdSalaryChargeAmt,
			BigDecimal bdSalaryExciseAmt, Long salaryProductChargeId,
			String salaryProductChargeName, Long salaryExciseDutyId,
			String salaryExciseDutyName, Long salaryMonthYearId) {
		// Get the payroll numbers from MemberSalary given month, year and
		// employerCode
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);

		BigDecimal bdTotalSalaryPosted = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryCharge = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryExciseDuty = BigDecimal.ZERO;
		BigDecimal bdNetSalaryAmt = BigDecimal.ZERO;

		BigDecimal bdTotalPrincipalPaid = BigDecimal.ZERO;
		BigDecimal bdTotalInterestPaid = BigDecimal.ZERO;
		BigDecimal bdTotalInsurancePaid = BigDecimal.ZERO;

		Long memberAccountId = null;
		String payrollNumber = null;

		String accountTransactionParentId = null;

		List<GenericValue> listLoanRepayments = null;

		// Create One AcctgTrans
		GenericValue accountTransaction = null;
		String acctgTransId = AccHolderTransactionServices
				.creatAccountTransRecord(accountTransaction, userLogin);

		for (GenericValue genericValue : listMemberSalary) {

			listLoanRepayments = new ArrayList<GenericValue>();

			// ###### Add the Net Salary to Member Account
			bdNetSalaryAmt = genericValue.getBigDecimal("netSalary");
			bdTotalSalaryPosted = bdTotalSalaryPosted.add(bdNetSalaryAmt);

			accountTransactionParentId = AccHolderTransactionServices
					.getcreateAccountTransactionParentId(memberAccountId,
							userLogin);
			payrollNumber = genericValue.getString("payrollNumber");
			// memberAccountId = LoanUtilities.getS
			// Add Net Salary to the Savings Account
			memberAccountId = AccHolderTransactionServices
					.getMemberSavingsAccountId(payrollNumber);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdNetSalaryAmt, memberAccountId, userLogin,
					"SALARYPROCESSING", accountTransactionParentId, null,
					acctgTransId, null, null);

			// ###### Deduct the Salary Processing Fee
			// Deduct the Salary Charge
			// Add Salary Charge
			bdTotalSalaryCharge = bdTotalSalaryCharge.add(bdSalaryChargeAmt);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdSalaryChargeAmt, memberAccountId, userLogin,
					salaryProductChargeName, accountTransactionParentId,
					salaryProductChargeId.toString(), acctgTransId, null, null);

			// ####### Deduct the Excise Duty
			// Deduct the Salary Charge
			// Add Salary Charge
			bdTotalSalaryExciseDuty = bdTotalSalaryExciseDuty
					.add(bdSalaryExciseAmt);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdSalaryExciseAmt, memberAccountId, userLogin,
					salaryExciseDutyName, accountTransactionParentId,
					salaryExciseDutyId.toString(), acctgTransId, null, null);

			// ####### Deduct the total Loan Deductions
			GenericValue member = RemittanceServices
					.getMemberByPayrollNo(payrollNumber);
			List<Long> listLoanApplicationIds = LoanServices
					.getDisbursedLoansIds(member.getLong("partyId"));
			//BigDecimal bdMemberTotalLoanExpectedAmt = BigDecimal.ZERO;
			BigDecimal bdMemberTotalDeductedAmount = BigDecimal.ZERO;
			BigDecimal bdLoanDeductedAmount = BigDecimal.ZERO;
			//BigDecimal bdLoanExpectedAmt = BigDecimal.ZERO;
			// String productName = "";
			GenericValue loanRepayment;
			Long loanRepaymentId = null;
			Delegator delegator = DelegatorFactoryImpl.getDelegator(null);

			BigDecimal bdSalaryBalance = bdNetSalaryAmt.subtract(
					bdSalaryChargeAmt).subtract(bdSalaryExciseAmt);
			
			for (Long loanApplicationId : listLoanApplicationIds) {
				BigDecimal totalLoanDue = LoanRepayments
						.getTotalLoanByLoanDue(loanApplicationId.toString());
				
				BigDecimal totalInsuranceDue = LoanRepayments
						.getTotalInsurancByLoanDue(loanApplicationId.toString());
				
				BigDecimal totalInterestDue = LoanRepayments
						.getTotalInterestByLoanDue(loanApplicationId.toString());
				
				BigDecimal totalPrincipalDue = LoanRepayments
						.getTotalPrincipaByLoanDue(loanApplicationId.toString());

				totalLoanDue = totalInterestDue.add(totalInsuranceDue).add(totalPrincipalDue);
				
				BigDecimal interestAmount = BigDecimal.ZERO;
				BigDecimal insuranceAmount = BigDecimal.ZERO;
				BigDecimal principalAmount = BigDecimal.ZERO;

				
				//Get the Insurance
				if (bdSalaryBalance.compareTo(totalInsuranceDue) > 0){
					insuranceAmount = totalInsuranceDue;
					bdSalaryBalance = bdSalaryBalance.subtract(insuranceAmount);
				} else {
					insuranceAmount = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				//If Insurance was overpaid , dont pay
				if (insuranceAmount.compareTo(BigDecimal.ZERO) == -1)
				{
					insuranceAmount = BigDecimal.ZERO;
				}
				
				
				//Get the Interest
				if (bdSalaryBalance.compareTo(totalInterestDue) > 0){
					interestAmount = totalInterestDue;
					bdSalaryBalance = bdSalaryBalance.subtract(interestAmount);
				} else {
					interestAmount = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				//If interest was overpaid , dont pay
				if (interestAmount.compareTo(BigDecimal.ZERO) == -1)
				{
					interestAmount = BigDecimal.ZERO;
				}
				
				
				//Get the Principal
				
				BigDecimal bdLoanBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
				if (totalPrincipalDue.compareTo(bdLoanBalance) > 0){
					principalAmount = bdLoanBalance;
				} else{
					principalAmount = totalPrincipalDue;
				}
				
				if (bdLoanBalance.compareTo(BigDecimal.ZERO) < 0){
					principalAmount = BigDecimal.ZERO;
				}
				
				if (bdSalaryBalance.compareTo(principalAmount) > 0){
					//principalAmount = principalAmount;
					bdSalaryBalance = bdSalaryBalance.subtract(principalAmount);
				} else {
					principalAmount = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				
				//Deducted will be Insurance + Interest + Principal
				bdLoanDeductedAmount = insuranceAmount.add(interestAmount).add(principalAmount);
				

				bdMemberTotalDeductedAmount = bdMemberTotalDeductedAmount
						.add(bdLoanDeductedAmount);
				
				if (bdLoanDeductedAmount.compareTo(BigDecimal.ZERO) < 1)
					continue;
				
				
				AccHolderTransactionServices.memberTransactionDeposit(
						bdLoanDeductedAmount, memberAccountId, userLogin,
						"LOANREPAYMENT", accountTransactionParentId, null,
						acctgTransId, null, loanApplicationId);

				
//				BigDecimal interestAmount = totalInterestDue;
//				BigDecimal insuranceAmount = totalInsuranceDue;
//				BigDecimal principalAmount = totalPrincipalDue;

				bdTotalPrincipalPaid = bdTotalPrincipalPaid
						.add(principalAmount);
				bdTotalInterestPaid = bdTotalInterestPaid.add(interestAmount);
				bdTotalInsurancePaid = bdTotalInsurancePaid
						.add(insuranceAmount);

				loanRepaymentId = delegator.getNextSeqIdLong("LoanRepayment");
				loanRepayment = delegator.makeValue("LoanRepayment", UtilMisc
						.toMap("loanRepaymentId", loanRepaymentId, "isActive",
								"Y",
								"createdBy",
								userLogin.get("userLoginId"),
								// "transactionType", "LOANREPAYMENT",
								"loanApplicationId", loanApplicationId,
								"partyId", member.getLong("partyId"),

								"transactionAmount", bdLoanDeductedAmount,

								"totalLoanDue", totalLoanDue,

								"totalInterestDue", totalInterestDue,

								"totalInsuranceDue", totalInsuranceDue,

								"totalPrincipalDue", totalPrincipalDue,

								"interestAmount", interestAmount,
								"insuranceAmount", insuranceAmount,
								"principalAmount", principalAmount,
								"repaymentMode", "SALARYPROCESSING",
								"month", month+year,
								"acctgTransId", acctgTransId

						));
				
				try {
					TransactionUtil.begin();
				} catch (GenericTransactionException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				try {
					delegator.createOrStore(loanRepayment);
				} catch (GenericEntityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					TransactionUtil.commit();
				} catch (GenericTransactionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// LoanRepayments.repayLoanWithoutDebitingCash(loanRepayment,
				// userLogin);
				// TODO use this to calcuate whether a loan can be paid
				// listLoanRepayments.add(loanRepayment);
				
				//If the loan balance after this is ZERO then clear / set loan as cleared
				BigDecimal newLoanBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
				if (newLoanBalance.compareTo(BigDecimal.ZERO) < 1){
					//Clear Loan
					LoanRepayments.clearLoan(loanApplicationId, userLogin, "CLeared by salary processing");
				}
			}

			// Repay Loan with total loan repaid amount above for each loan
			// Make contribution to each account with the contribution amount

			// GL POsting
			// -- sum value for net salary - (salary processing fee + excise
			// duty + total loans + total account contributions)

			genericValue.set("processed", "Y");
			// listSalaryToUpdate.add(genericValue);
			try {
				delegator.createOrStore(genericValue);
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Post Total Net Salary in GL bdTotalSalaryPosted

		// SALARYPROCESSING
		// GenericValue accountHolderTransactionSetup =
		// AccHolderTransactionServices
		// .getAccountHolderTransactionSetup("SALARYPROCESSING");
		// String debitAccountId = accountHolderTransactionSetup
		// .getString("cashAccountId");
		// String creditAccountId = accountHolderTransactionSetup
		// .getString("memberDepositAccId");
		GenericValue accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("STATIONACCOUNTPAYMENT");

		String stationDepositAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");
		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		// salaryProductChargeId = salaryProductChargeId.replaceAll(",", "");
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		Long entrySequence = 1L;
		// ------------------------
		// Debit Leaf Base with the total
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalSalaryPosted, acctgTransId, "D",
				stationDepositAccountId, entrySequence.toString(), branchId);

		BigDecimal bdTotalCharges = bdTotalSalaryCharge
				.add(bdTotalSalaryExciseDuty);
		BigDecimal bdTotalLoanAmountPaid = bdTotalPrincipalPaid.add(
		bdTotalInterestPaid).add(bdTotalInsurancePaid);
		BigDecimal bdTotalMemberDepositAmt = bdTotalSalaryPosted
				.subtract(bdTotalCharges);
		bdTotalMemberDepositAmt = bdTotalMemberDepositAmt.subtract(bdTotalLoanAmountPaid);
		// Credit Member Deposits with (total net - (total charge + total excise
		// duty))
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalMemberDepositAmt, acctgTransId, "C",
				savingsAccountGLAccountId, entrySequence.toString(), branchId);
		// Credit Salary Charge with total salary charge
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalSalaryCharge, acctgTransId,
						"C", salaryChargeCreditAccountId,
						entrySequence.toString(), branchId);
		// Credit Excise Duty with total excise duty
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalSalaryExciseDuty,
						acctgTransId, "C", salaryExciseCreditAccountId,
						entrySequence.toString(), branchId);

		// Post the Loan Repayments
		// DR the savings withdrawable savingsAccountGLAccountId
//		BigDecimal bdTotalLoanAmountPaid = bdTotalPrincipalPaid.add(
//				bdTotalInterestPaid).add(bdTotalInsurancePaid);
//		entrySequence = entrySequence + 1;
//		AccHolderTransactionServices.createAccountPostingEntry(
//				bdTotalLoanAmountPaid, acctgTransId, "D",
//				savingsAccountGLAccountId, entrySequence.toString(), branchId);

		// Principal Payment
		// CR Loan Receivable bdTotalPrincipalPaid
		entrySequence = entrySequence + 1;

		accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("PRINCIPALPAYMENT");
		String loanReceivableAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalPrincipalPaid, acctgTransId, "C",
				loanReceivableAccountId, entrySequence.toString(), branchId);

		// Interest Payment
		// CR Interest Recevable bdTotalInterestPaid
		entrySequence = entrySequence + 1;

		accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("INTERESTPAYMENT");
		String interestReceivableAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		AccHolderTransactionServices
				.createAccountPostingEntry(bdTotalInterestPaid, acctgTransId,
						"C", interestReceivableAccountId,
						entrySequence.toString(), branchId);

		// Insurance Payment
		// CR Charge/Insurance Receivable bdTotalInsurancePaid
		entrySequence = entrySequence + 1;

		accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("INSURANCEPAYMENT");
		String insurancePaymentAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");

		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalInsurancePaid, acctgTransId, "C",
				insurancePaymentAccountId, entrySequence.toString(), branchId);

		// DR
		// TODO May use it to check loan Repayment
		// for (GenericValue genericValue : listLoanRepayments) {
		// //entrySequence = entrySequence + 1;
		// LoanRepayments.repayLoanWithoutDebitingCash(genericValue, userLogin,
		// entrySequence, acctgTransId);
		// }

		// listLoanRepayments.add(loanRepayment);

		// Update the MemberSalary to processed

		// Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		// try {
		// delegator.storeAll(listSalaryToUpdate);
		// } catch (GenericEntityException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		// Prior Transaction when a cheque was paid
		// Debit Cash at Bank
		// Credit Leaf Base

	}

	/****
	 * @author Japheth Odonya @when Jun 18, 2015 1:00:06 AM
	 * 
	 *         doProcessingAccountsOnlyDeductions
	 * */
	private static void doProcessingAccountsOnlyDeductions(
			Map<String, String> userLogin, String month, String year,
			String employerCode, BigDecimal bdSalaryChargeAmt,
			BigDecimal bdSalaryExciseAmt, Long salaryProductChargeId,
			String salaryProductChargeName, Long salaryExciseDutyId,
			String salaryExciseDutyName, Long salaryMonthYearId) {
		// Get the payroll numbers from MemberSalary given month, year and
		// employerCode
		List<GenericValue> listMemberSalary = getMemberSalaryList(month, year,
				employerCode, salaryMonthYearId);

		BigDecimal bdTotalSalaryPosted = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryCharge = BigDecimal.ZERO;
		BigDecimal bdTotalSalaryExciseDuty = BigDecimal.ZERO;
		BigDecimal bdNetSalaryAmt = BigDecimal.ZERO;

		Long memberAccountId = null;
		String payrollNumber = null;

		String accountTransactionParentId = null;

		// Create One AcctgTrans
		GenericValue accountTransaction = null;
		String acctgTransId = AccHolderTransactionServices
				.creatAccountTransRecord(accountTransaction, userLogin);

		Set<Long> contributingProductIds = new HashSet<Long>();
		Map<Long, BigDecimal> productTotals = new HashMap<Long, BigDecimal>();
		for (GenericValue genericValue : listMemberSalary) {

			// ###### Add the Net Salary to Member Account
			bdNetSalaryAmt = genericValue.getBigDecimal("netSalary");
			bdTotalSalaryPosted = bdTotalSalaryPosted.add(bdNetSalaryAmt);

			accountTransactionParentId = AccHolderTransactionServices
					.getcreateAccountTransactionParentId(memberAccountId,
							userLogin);
			payrollNumber = genericValue.getString("payrollNumber");
			// memberAccountId = LoanUtilities.getS
			// Add Net Salary to the Savings Account
			memberAccountId = AccHolderTransactionServices
					.getMemberSavingsAccountId(payrollNumber);
			AccHolderTransactionServices.memberTransactionDeposit(
					bdNetSalaryAmt, memberAccountId, userLogin,
					"SALARYPROCESSING", accountTransactionParentId, null,
					acctgTransId, null, null);

			// ###### Deduct the Salary Processing Fee
			// Deduct the Salary Charge
			// Add Salary Charge
//			bdTotalSalaryCharge = bdTotalSalaryCharge.add(bdSalaryChargeAmt);
//			AccHolderTransactionServices.memberTransactionDeposit(
//					bdSalaryChargeAmt, memberAccountId, userLogin,
//					salaryProductChargeName, accountTransactionParentId,
//					salaryProductChargeId.toString(), acctgTransId);

			// ####### Deduct the Excise Duty
			// Deduct the Salary Charge
			// Add Salary Charge
//			bdTotalSalaryExciseDuty = bdTotalSalaryExciseDuty
//					.add(bdSalaryExciseAmt);
//			AccHolderTransactionServices.memberTransactionDeposit(
//					bdSalaryExciseAmt, memberAccountId, userLogin,
//					salaryExciseDutyName, accountTransactionParentId,
//					salaryExciseDutyId.toString(), acctgTransId);

			// ####### Deduct the total Loan Deductions
			GenericValue member = RemittanceServices
					.getMemberByPayrollNo(payrollNumber);

			Delegator delegator = DelegatorFactoryImpl.getDelegator(null);

			BigDecimal bdSalaryBalance = bdNetSalaryAmt.subtract(
					bdSalaryChargeAmt).subtract(bdSalaryExciseAmt);

			// Deduct the total Account Contributions
			List<GenericValue> listMemberAccounts = LoanUtilities
					.getMemberContributingAccounts(member.getLong("partyId"));
			Long memberSavingsAccountId = LoanUtilities
					.getMemberAccountIdFromMemberAccount(
							member.getLong("partyId"),
							LoanUtilities
									.getAccountProductGivenCodeId(
											AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE)
									.getLong("accountProductId"));
			BigDecimal bdTotalContributingAmount = BigDecimal.ZERO;
			BigDecimal bdContributingAmt;
			for (GenericValue memberAccount : listMemberAccounts) {

				bdContributingAmt = LoanUtilities.getContributionAmount(
						memberAccount, member.getLong("partyId"));
				
				if (bdSalaryBalance.compareTo(bdContributingAmt) > 0){
					bdSalaryBalance = bdSalaryBalance.subtract(bdContributingAmt);
					
				} else{
					bdContributingAmt = bdSalaryBalance;
					bdSalaryBalance = BigDecimal.ZERO;
				}
				
				if (bdContributingAmt.compareTo(BigDecimal.ZERO) > 0){

				
				contributingProductIds.add(memberAccount
						.getLong("accountProductId"));

				if (!productTotals.containsKey(memberAccount
						.getLong("accountProductId"))) {
					productTotals.put(
							memberAccount.getLong("accountProductId"),
							bdContributingAmt);
				} else {
					productTotals.put(
							memberAccount.getLong("accountProductId"),
							productTotals.get(
									memberAccount.getLong("accountProductId"))
									.add(bdContributingAmt));
				}

				// remove it from source account - the savings account
				AccHolderTransactionServices.memberTransactionDeposit(
						bdContributingAmt, memberSavingsAccountId, userLogin,
						"TOOTHERACCOUNTS", accountTransactionParentId, null,
						acctgTransId, memberAccount.getLong("accountProductId"), null);
				// Add to destination account e.g. member deposits
				AccHolderTransactionServices.memberTransactionDeposit(
						bdContributingAmt,
						memberAccount.getLong("memberAccountId"), userLogin,
						"DEPOSITFROMSALARY", accountTransactionParentId, null,
						acctgTransId, null, null);
				bdTotalContributingAmount = bdTotalContributingAmount
						.add(bdContributingAmt);
				
			}
			}
			// Repay Loan with total loan repaid amount above for each loan
			// Make contribution to each account with the contribution amount

			// GL POsting
			// -- sum value for net salary - (salary processing fee + excise
			// duty + total loans + total account contributions)

			genericValue.set("processed", "Y");
			// listSalaryToUpdate.add(genericValue);
			try {
				delegator.createOrStore(genericValue);
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Post Total Net Salary in GL bdTotalSalaryPosted

		// SALARYPROCESSING
		// GenericValue accountHolderTransactionSetup =
		// AccHolderTransactionServices
		// .getAccountHolderTransactionSetup("SALARYPROCESSING");
		// String debitAccountId = accountHolderTransactionSetup
		// .getString("cashAccountId");
		// String creditAccountId = accountHolderTransactionSetup
		// .getString("memberDepositAccId");
		GenericValue accountHolderTransactionSetup = AccHolderTransactionServices
				.getAccountHolderTransactionSetup("STATIONACCOUNTPAYMENT");

		String stationDepositAccountId = accountHolderTransactionSetup
				.getString("memberDepositAccId");
		String savingsAccountGLAccountId = LoanUtilities
				.getGLAccountIDForAccountProduct(AccHolderTransactionServices.SAVINGS_ACCOUNT_CODE);

		// salaryProductChargeId = salaryProductChargeId.replaceAll(",", "");
		GenericValue salaryProductCharge = LoanUtilities
				.getProductCharge(salaryProductChargeId);
		String salaryChargeCreditAccountId = salaryProductCharge
				.getString("chargeAccountId");

		GenericValue salaryExciseDutyProductCharge = LoanUtilities
				.getProductCharge(salaryExciseDutyId);
		String salaryExciseCreditAccountId = salaryExciseDutyProductCharge
				.getString("chargeAccountId");

		String branchId = AccHolderTransactionServices
				.getEmployeeBranch((String) userLogin.get("partyId"));
		Long entrySequence = 1L;
		// ------------------------
		// Debit Leaf Base with the total
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalSalaryPosted, acctgTransId, "D",
				stationDepositAccountId, entrySequence.toString(), branchId);

		BigDecimal bdTotalCharges = bdTotalSalaryCharge
				.add(bdTotalSalaryExciseDuty);
		BigDecimal bdTotalMemberDepositAmt = bdTotalSalaryPosted;
				//.subtract(bdTotalCharges);

		// Credit Member Deposits with (total net - (total charge + total excise
		// duty))
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalMemberDepositAmt, acctgTransId, "C",
				savingsAccountGLAccountId, entrySequence.toString(), branchId);
		// Credit Salary Charge with total salary charge
//		entrySequence = entrySequence + 1;
//		AccHolderTransactionServices
//				.createAccountPostingEntry(bdTotalSalaryCharge, acctgTransId,
//						"C", salaryChargeCreditAccountId,
//						entrySequence.toString(), branchId);
		// Credit Excise Duty with total excise duty
//		entrySequence = entrySequence + 1;
//		AccHolderTransactionServices
//				.createAccountPostingEntry(bdTotalSalaryExciseDuty,
//						acctgTransId, "C", salaryExciseCreditAccountId,
//						entrySequence.toString(), branchId);

		// Posting Contributed Accounts Member Deposits, Share Capital etc
		// Get the toal Account Amounts Contributed
		BigDecimal bdTotalAmountContributedToAccounts = BigDecimal.ZERO;

		String productglAccountId = "";

		for (Long accountProductId : contributingProductIds) {

			// Credit each account product there after

			entrySequence = entrySequence + 1;
			productglAccountId = LoanUtilities
					.getGLAccountIDForAccountProduct(LoanUtilities
							.getAccountProduct(accountProductId)
							.getString("code").trim());
			AccHolderTransactionServices.createAccountPostingEntry(
					productTotals.get(accountProductId), acctgTransId, "C",
					productglAccountId, entrySequence.toString(), branchId);

			bdTotalAmountContributedToAccounts = bdTotalAmountContributedToAccounts
					.add(productTotals.get(accountProductId));

		}

		// Debit savings with total account contributions
		entrySequence = entrySequence + 1;
		AccHolderTransactionServices.createAccountPostingEntry(
				bdTotalAmountContributedToAccounts, acctgTransId, "D",
				savingsAccountGLAccountId, entrySequence.toString(), branchId);

	}

}
