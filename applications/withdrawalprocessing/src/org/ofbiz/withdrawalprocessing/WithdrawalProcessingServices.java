package org.ofbiz.withdrawalprocessing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.ofbiz.accountholdertransactions.AccHolderTransactionServices;
import org.ofbiz.accountholdertransactions.LoanUtilities;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactoryImpl;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.loansprocessing.LoansProcessingServices;

/**
 * @author Japheth Odonya  @when Jun 19, 2015 10:27:03 AM
 * 
 * Member Withdrawal Processing
 * 
 * org.ofbiz.withdrawalprocessing.WithdrawalProcessingServices.getLoanBalance
 * */
public class WithdrawalProcessingServices {
	
	private static Logger log = Logger
			.getLogger(WithdrawalProcessingServices.class);
	public static Long PERCENT = 100L;

	
	public static BigDecimal getLoanBalance(Long loanApplicationId){
		
		log.info(" HHHHHHHHHHHHHHH "+loanApplicationId);
		BigDecimal bdBalance = BigDecimal.ZERO;
		bdBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
		return bdBalance;
	}
	
	
	/***
	 * @author Japheth Odonya  @when Jun 19, 2015 12:14:35 PM
	 * Get the loan member name
	 * */
	public static String getMemberGuaranteed(Long loanApplicationId){
		String names = "";
		
		names  = LoanUtilities.getMemberNameGivenLoanApplicationId(loanApplicationId);
		
		return names;
	}
	
	/****
	 * @author Japheth Odonya  @when Jun 19, 2015 12:51:03 PM
	 * */
	public static String getMemberNumber(Long loanApplicationId){
		String memberNumber = "";
		
		memberNumber  = LoanUtilities.getMemberNumberGivenLoanApplicationId(loanApplicationId);
		
		return memberNumber;
	}
	
	/***
	 * @author Japheth Odonya  @when Jun 19, 2015 12:48:45 PM
	 * **/
	public static String getPayrollNumber(Long loanApplicationId){
		String payrollNumber = "";
		
		payrollNumber  = LoanUtilities.getPayrollNumberGivenLoanApplicationId(loanApplicationId);
		
		return payrollNumber;
	}
	
	/****
	 * @author Japheth Odonya  @when Jun 19, 2015 12:48:27 PM
	 * */
	public static String getMobileNumber(Long loanApplicationId){
		String mobileNumber = "";
		
		mobileNumber  = LoanUtilities.getMobileNumberGivenLoanApplicationId(loanApplicationId);
		
		return mobileNumber;
	}
	
	//getMemberStationName
	public static String getMemberStationName(Long loanApplicationId){
		String stationName = "";
		
		stationName  = LoanUtilities.getMemberStationName(loanApplicationId);
		
		return stationName;
	}
	
	
	//Get Guaranteed %
	public static BigDecimal getLoanGuarateedPercByGuarantor(Long loanApplicationId, Long guarantorId){
		
		BigDecimal bdLoanBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
		
		Long noOfGuarators = new Long(LoansProcessingServices.getNumberOfGuarantors(loanApplicationId).size());
		
		BigDecimal perGuarantorTotal = bdLoanBalance.divide(new BigDecimal(noOfGuarators), 2, RoundingMode.HALF_EVEN);
		
		if (bdLoanBalance.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;
		
		
		BigDecimal bdPercentage = 	perGuarantorTotal.divide(bdLoanBalance, 2, RoundingMode.HALF_EVEN).multiply(new BigDecimal(PERCENT));
		
		return bdPercentage;
	}
	
	//Get Guaranteed Amount
	public static BigDecimal getLoanGuarateedAmountByGuarantor(Long loanApplicationId, Long guarantorId){
		BigDecimal bdLoanBalance = LoansProcessingServices.getTotalLoanBalancesByLoanApplicationId(loanApplicationId);
		
		Long noOfGuarators = new Long(LoansProcessingServices.getNumberOfGuarantors(loanApplicationId).size());
		
		BigDecimal perGuarantorTotal = bdLoanBalance.divide(new BigDecimal(noOfGuarators), 2, RoundingMode.HALF_EVEN);
		
		return perGuarantorTotal;
	}
	
	
	/***
	 * @author Japheth Odonya  @when Jun 19, 2015 1:47:21 PM
	 * 
	 * Get Loan Amount
	 * */
	public static BigDecimal getLoanAmount(Long loanApplicationId){
		BigDecimal loanAmt = BigDecimal.ZERO;
		
		loanAmt  = LoanUtilities.getLoanAmountGivenLoanApplicationId(loanApplicationId);
		
		return loanAmt;
	}
	
	/****
	 * @author Japheth Odonya  @when Jun 19, 2015 2:38:24 PM
	 * 
	 * Get Share Capital Amount
	 * */
	public static BigDecimal getShareCapitalBalance(String partyId){
		
		Long partyIdLong = Long.valueOf(partyId);
		BigDecimal bdShareCapital = BigDecimal.ZERO;
		
		bdShareCapital = LoanUtilities.getShareCapitalAmount(partyIdLong);
		
		return bdShareCapital;
	}
	
	public static BigDecimal getMemberDepositBalance(String partyId){
		BigDecimal bdMemberDeposit = BigDecimal.ZERO;
		Long partyIdLong = Long.valueOf(partyId);
		bdMemberDeposit = LoanUtilities.getMemberDepositAmount(partyIdLong);
		return bdMemberDeposit;
	}
	
	public static String getPartIdGivenMemberWithdrawalId(Long memberWithdrawalId){
		String partyId = null;
		
		GenericValue memberWithdrawal = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			memberWithdrawal = delegator.findOne("MemberWithdrawal",
					UtilMisc.toMap("memberWithdrawalId", memberWithdrawalId), false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}
		partyId = memberWithdrawal.getLong("partyId").toString();
		
		return partyId;
	}
	
	/***
	 * Get Total Loan Balance
	 * */
	public static BigDecimal getLoanTotalLoanBalance(String partyId){
		BigDecimal bdTotalLoanBalance = BigDecimal.ZERO;
		Long partyIdLong = Long.valueOf(partyId);
		bdTotalLoanBalance = LoansProcessingServices.getTotalDisbursedLoanBalances(partyIdLong);
				//LoansProcessingServices.getTotalLoanBalances(memberId, loanProductId);
		return bdTotalLoanBalance;
	}
	
	/****
	 * @author Japheth Odonya  @when Jun 23, 2015 7:36:39 PM
	 * 
	 * Total Guaranteed Amount by Member
	 * */
	public static BigDecimal getTotalGuaranteedAmount(String partyId){
		Long partyIdLong = Long.valueOf(partyId);
		BigDecimal bdTotalGuaranteedAmount = BigDecimal.ZERO;
		
		//Get the list of guarateed loans
		//LoanGuarantorDisbursedLoan
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		List<GenericValue> loanGuarantorDisbursedLoanELI = new ArrayList<GenericValue>();
		
		EntityConditionList<EntityExpr> loanGuarantorDisbursedLoanConditions = EntityCondition
				.makeCondition(
						UtilMisc.toList(EntityCondition.makeCondition(
								"guarantorId", EntityOperator.EQUALS,
								partyIdLong),
								
								EntityCondition.makeCondition(
										"loanStatusId", EntityOperator.EQUALS,
										6L)
								
								
								), EntityOperator.AND);
		
		try {
			loanGuarantorDisbursedLoanELI = delegator.findList("LoanGuarantorDisbursedLoan",
					loanGuarantorDisbursedLoanConditions, null, null,
					null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		for (GenericValue genericValue : loanGuarantorDisbursedLoanELI) {
			bdTotalGuaranteedAmount = bdTotalGuaranteedAmount.add(getLoanGuarateedAmountByGuarantor(genericValue.getLong("loanApplicationId") , genericValue.getLong("guarantorId")));
		}
		
		
		return bdTotalGuaranteedAmount;
	}
	
	/***
	 * @author Japheth Odonya  @when Jun 23, 2015 7:49:56 PM
	 * 
	 * Share Capital Limit
	 * */
	public static BigDecimal getShareCapitalMinimum(String partyId){
		//Get Member 
		
		GenericValue member = LoanUtilities.getMember(partyId);
		
		
		Long memberClassId = member.getLong("memberClassId");
		
		if (memberClassId == null){
			memberClassId = LoanUtilities.getMemberClassId("Class A");
		}
		
		BigDecimal bdShareCapitalLimit = getMinimumShareCapital(memberClassId);

		
		//BigDecimal bdShareCapitalLimit = new BigDecimal(20000);
		return bdShareCapitalLimit;
	}


	private static BigDecimal getMinimumShareCapital(Long memberClassId) {
		GenericValue shareMinimum = LoanUtilities.getShareMinimumEntity(memberClassId);
		
		if (shareMinimum == null)
			return null;
		
		return shareMinimum.getBigDecimal("minShareCapital");
	}
	
	
	/***
	 * @author Japheth Odonya  @when Jun 30, 2015 1:20:26 PM
	 * 
	 * Update Share Capital for a member
	 * 
	 * */
	public static String updateMemberShareCapitalWithMemberDeposits(Long partyId, Map<String, String> userLogin){
		
		BigDecimal bdShareCapitalBalance = BigDecimal.ZERO;
		bdShareCapitalBalance = LoanUtilities.getShareCapitalAccountBalance(partyId);
		bdShareCapitalBalance = bdShareCapitalBalance.setScale(2, RoundingMode.HALF_EVEN);
		
		BigDecimal bdShareCapitalLimit = LoanUtilities.getShareCapitalLimit(partyId);
		bdShareCapitalLimit = bdShareCapitalLimit.setScale(2, RoundingMode.HALF_EVEN);
		
		BigDecimal bdMemberDepositAmount = LoanUtilities.getMemberDepositAmount(partyId);
		bdMemberDepositAmount = bdMemberDepositAmount.setScale(2, RoundingMode.HALF_EVEN);
		
		if (bdShareCapitalBalance.compareTo(bdShareCapitalLimit) == 0)
			return "The minimum share limit already met, no need for offsetting !!";
		
		BigDecimal bdShareCapitalDeficit = bdShareCapitalLimit.subtract(bdShareCapitalBalance);
		
		if (bdMemberDepositAmount.compareTo(bdShareCapitalDeficit) == -1)
			return "The Member deposit amount is not enough to pay for the share capital deficit! Please pay over the counter";
		
		//(String sourceMemberAccountId, String destinationMemberAccountId, BigDecimal bdAmount, Map<String, String> userLogin)
		//Do an account transfer - from member deposit to share capital be the deficit amount
		String sourceMemberAccountId = LoanUtilities.getMemberAccountIdGivenMemberAndAccountCode(partyId, AccHolderTransactionServices.MEMBER_DEPOSIT_CODE);
		String destinationMemberAccountId = LoanUtilities.getMemberAccountIdGivenMemberAndAccountCode(partyId, AccHolderTransactionServices.SHARE_CAPITAL_CODE);
		AccHolderTransactionServices.accountTransferTransaction(sourceMemberAccountId, destinationMemberAccountId, bdShareCapitalDeficit, userLogin);
		//AccHolderTransactionServices.postTransactionEntry(delegator, bdLoanAmount, branchId, partyId, loanReceivableAccount, postingType, acctgTransId, acctgTransType, entrySequenceId);
		
		return "success";
	}
	
	/******
	 * @author Japheth Odonya  @when Jun 30, 2015 1:23:04 PM
	 * 
	 * Offset Disbursed Loans With Member Deposits
	 * **/
	public static String offsetDisbursedLoansWithMemberDeposits(Long partyId, Map<String, String> userLogin){
		
		BigDecimal bdShareCapitalBalance = BigDecimal.ZERO;
		bdShareCapitalBalance = LoanUtilities.getShareCapitalAccountBalance(partyId);
		bdShareCapitalBalance = bdShareCapitalBalance.setScale(2, RoundingMode.HALF_EVEN);
		
		BigDecimal bdShareCapitalLimit = LoanUtilities.getShareCapitalLimit(partyId);
		bdShareCapitalLimit = bdShareCapitalLimit.setScale(2, RoundingMode.HALF_EVEN);
		
		BigDecimal bdMemberDepositAmount = LoanUtilities.getMemberDepositAmount(partyId);
		bdMemberDepositAmount = bdMemberDepositAmount.setScale(2, RoundingMode.HALF_EVEN);
		
		if (bdShareCapitalBalance.compareTo(bdShareCapitalLimit) == -1)
			return "Please update share capital to share limit amount first before trying to offset loans. Share Limit amount is "+bdShareCapitalLimit+" while your share balance is "+bdShareCapitalBalance;

		//Get Member Withdrawal Commission
		GenericValue memberWithdrawalCommission = LoanUtilities.getProductChargeEntity("Member Withdrawal Commission");
		if (memberWithdrawalCommission == null)
			return "Please make sure that you have defined member withdrawal commission in the Product Charge using the name 'Member Withdrawal Commission'";
		
		if (memberWithdrawalCommission.getString("chargeAccountId") == null)
			return "Withdrawal commission charge must have a gl account specified for correct posting of the GL";

		if (memberWithdrawalCommission.getBigDecimal("fixedAmount") == null)
			return "Please specify the Member Withdrawal Commission Charge Amount in the Product Charges";


		GenericValue memberWithdrawalExciseDuty = LoanUtilities.getProductChargeEntity("Member Withdrawal Excise Duty");
		if (memberWithdrawalExciseDuty == null)
			return "Please make sure that you have defined member withdrawal Excise Duty in the Product Charge using the name 'Member Withdrawal Excise Duty'";
		
		if (memberWithdrawalExciseDuty.getString("chargeAccountId") == null)
			return "Withdrawal Excise Duty must have a gl account specified for correct posting of the GL";

		if (memberWithdrawalExciseDuty.getBigDecimal("rateAmount") == null)
			return "Please specify the Member Withdrawal Excise Charge Amount in the Product Charges";

		BigDecimal bdMemberWithdrawalCommissionAmount = memberWithdrawalCommission.getBigDecimal("fixedAmount");
		
		BigDecimal bdMemberWithdrawalExciseDuty = memberWithdrawalExciseDuty.getBigDecimal("rateAmount").multiply(bdMemberWithdrawalCommissionAmount).divide(new BigDecimal(100), 4, RoundingMode.HALF_EVEN);
		BigDecimal bdTotalCharge = bdMemberWithdrawalCommissionAmount.add(bdMemberWithdrawalExciseDuty);
		
		//Check if member deposits is enough to offset loans (more than loans and withdrawal commission)
		BigDecimal bdLoanBalancesTotal = LoansProcessingServices.getTotalDisbursedLoanBalances(partyId);
		
		BigDecimal bdMemberDepositBalance = getMemberDepositBalance(partyId.toString());
		
		BigDecimal bdTotalOffset = bdLoanBalancesTotal.add(bdTotalCharge);
		
		if (bdTotalOffset.compareTo(bdMemberDepositBalance) == -1){
			return " Member Deposits not enough to offset the loans balances and pay the charges Member Deposit Amount is "+bdMemberDepositAmount+" Loan Balance Amount plus commission is "+bdTotalOffset;
		}
		
		//Offset the loans
		
		//Pay commission
		
		return "success";
	}
}
