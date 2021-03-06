package org.ofbiz.atmmanagement;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import javolution.util.FastMap;

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
import org.ofbiz.webapp.event.EventHandlerException;

import com.google.gson.Gson;

/***
 * @author Japheth Odonya @when Nov 5, 2014 8:40:14 PM
 * 
 * */
public class ATMManagementServices {

	public static Logger log = Logger.getLogger(ATMManagementServices.class);
	public static BigDecimal EXCISEDUTYPERCENTAGE = new BigDecimal(10);

	public static String getMemberAccounts(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, Object> result = FastMap.newInstance();
		Delegator delegator = (Delegator) request.getAttribute("delegator");
		String partyId = (String) request.getParameter("partyId");
		partyId = partyId.replaceAll(",", "");
		List<GenericValue> memberAccountELI = null;
		EntityConditionList<EntityExpr> memberAccountConditions = EntityCondition
				.makeCondition(UtilMisc.toList(
						EntityCondition.makeCondition("partyId",
								EntityOperator.EQUALS, Long.valueOf(partyId)),
						EntityCondition.makeCondition("withdrawable",
								EntityOperator.EQUALS, "Yes")),
						EntityOperator.AND);
		try {
			memberAccountELI = delegator.findList("MemberAccount",
					memberAccountConditions, null, null, null, false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		if (memberAccountELI == null) {
			result.put("", "No Accounts");
		}

		GenericValue accountProduct;
		for (GenericValue genericValue : memberAccountELI) {
			accountProduct = LoanUtilities.getAccountProduct(genericValue
					.getLong("accountProductId"));
			result.put(
					genericValue.get("memberAccountId").toString(),
					genericValue.get("accountNo") + " - "
							+ accountProduct.getString("name") + " - "
							+ genericValue.getString("accountName"));
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

	public static String applyforATM(HttpServletRequest request,
			HttpServletResponse response) {
		// Map<String, Object> result = FastMap.newInstance();
		Delegator delegator = (Delegator) request.getAttribute("delegator");
		String cardApplicationId = (String) request
				.getParameter("cardApplicationId");
		HttpSession session = request.getSession();
		GenericValue userLogin = (GenericValue) session
				.getAttribute("userLogin");
		String userLoginId = userLogin.getString("userLoginId");

		GenericValue cardApplication = null;

		cardApplicationId = cardApplicationId.replaceAll(",", "");
		try {
			cardApplication = delegator.findOne(
					"CardApplication",
					UtilMisc.toMap("cardApplicationId",
							Long.valueOf(cardApplicationId)), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		//
		Long cardStatusId = getCardStatus("APPLIED");

		cardApplication.set("cardStatusId", cardStatusId);
		try {
			delegator.createOrStore(cardApplication);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		// Create Card Log
		GenericValue cardLog;
		Long cardLogId = delegator.getNextSeqIdLong("CardLog", 1);
		// loanApplicationId = loanApplicationId.replaceAll(",", "");
		cardLog = delegator.makeValue("CardLog", UtilMisc.toMap("cardLogId",
				cardLogId, "cardApplicationId", "isActive", "Y",
				Long.valueOf(cardApplicationId), "cardStatusId", cardStatusId,
				"createdBy", userLoginId, "comment", "Applied for ATM Card"));

		try {
			delegator.createOrStore(cardLog);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		return "success";
	}

	public static Long getCardStatus(String name) {
		List<GenericValue> cardStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			cardStatusELI = delegator.findList("CardStatus",
					EntityCondition.makeCondition("name", name), null, null,
					null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		Long cardStatusId = null;
		for (GenericValue genericValue : cardStatusELI) {
			cardStatusId = genericValue.getLong("cardStatusId");
		}
		return cardStatusId;
	}

	public static GenericValue getCardStatusEntity(String name) {
		List<GenericValue> cardStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			cardStatusELI = delegator.findList("CardStatus",
					EntityCondition.makeCondition("name", name), null, null,
					null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		GenericValue cardStatus = null;
		for (GenericValue genericValue : cardStatusELI) {
			cardStatus = genericValue;
		}
		return cardStatus;
	}

	public static String getCardStatusName(Long cardStatusId) {
		List<GenericValue> cardStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			cardStatusELI = delegator
					.findList("CardStatus", EntityCondition.makeCondition(
							"cardStatusId", cardStatusId), null, null, null,
							false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		String statusName = null;
		for (GenericValue genericValue : cardStatusELI) {
			statusName = genericValue.getString("name");
		}
		return statusName;
	}

	/****
	 * @author Japheth Odonya @when Nov 5, 2014 8:39:57 PM Check that Member Has
	 *         Enough Money on account Account Balance >= (Min Balance + Charge
	 *         Amount)
	 * **/
	public static String memberHasEnoughBalance(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, Object> result = FastMap.newInstance();
		Delegator delegator = (Delegator) request.getAttribute("delegator");

		Long cardApplicationId = Long.valueOf(request
				.getParameter("cardApplicationId"));

		String memberAccountId = getMemberAccountId(cardApplicationId);

		memberAccountId = memberAccountId.replaceAll(",", "");
		List<GenericValue> memberAccountELI = null;
		try {
			memberAccountELI = delegator.findList(
					"MemberAccount",
					EntityCondition.makeCondition("memberAccountId",
							Long.valueOf(memberAccountId)), null, null, null,
					false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		if (memberAccountELI == null) {
			result.put("", "No Accounts");
		}
		GenericValue memberAccount = null;
		for (GenericValue genericValue : memberAccountELI) {
			memberAccount = genericValue;
			// result.put(genericValue.get("memberAccountId").toString(),
			// genericValue.get("accountNo")+" - "+genericValue.getString("accountName"));
		}

		// Get Minimum Balance
		BigDecimal bdAccountMinimumBalanceAmount = getAccountMinimumBalance(memberAccount
				.getLong("accountProductId"));

		// Get Card Application Charge
		BigDecimal bdCardChargeAmount = getCardCharge("ATM Application Fee");

		// Get Member Balance for the Account
		// ( Opening Balance + Total Deposits and other increases (I) - Total
		// Withdrawals and Charges (D))
		BigDecimal bdMemberBalanceAmount = getMemberBalanceAmount(memberAccount);
		// Check if Member Balance >= Minimum Balance + Card Application Charge

		String balanceStatus = "NOTENOUGH";

		if (bdMemberBalanceAmount.compareTo(bdAccountMinimumBalanceAmount
				.add(bdCardChargeAmount)) > -1) {
			balanceStatus = "ENOUGH";
		}

		result.put("balanceStatus", balanceStatus);
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

	private static String getMemberAccountId(Long cardApplicationId) {
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		GenericValue cardApplication = null;

		try {
			cardApplication = delegator.findOne("CardApplication",
					UtilMisc.toMap("cardApplicationId", cardApplicationId),
					false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		String memberAccountId = cardApplication.getString("memberAccountId");
		return memberAccountId;
	}

	/***
	 * Get Member Balance Opening Balance + Deposits (Increments) - Withdrawals
	 * and Charges (D)
	 * */
	private static BigDecimal getMemberBalanceAmount(GenericValue memberAccount) {
		// TODO Auto-generated method stub
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		BigDecimal bdAccountBalanceAmount = AccHolderTransactionServices
				.getTotalSavings(memberAccount.getLong("memberAccountId")
						.toString(), delegator);

		log.info(" BBBBBBBBBBBBBB Balance is " + bdAccountBalanceAmount);
		return bdAccountBalanceAmount;
	}

	/***
	 * @author Japheth Odonya @when Nov 5, 2014 8:39:48 PM Get the Charge Amount
	 *         from the ProductCharge where all the charges are defined ** We
	 *         know that all card charges are fixed figures so we will just go
	 *         ahead and pick a value from the fixedAmount column of the product
	 *         charge **
	 * */
	private static BigDecimal getCardCharge(String chargeName) {
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		List<GenericValue> productChargeELI = null;
		try {
			productChargeELI = delegator.findList("ProductCharge",
					EntityCondition.makeCondition("name", chargeName), null,
					null, null, false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		BigDecimal bdProductChargeAmount = BigDecimal.ZERO;
		for (GenericValue genericValue : productChargeELI) {
			// result.put(genericValue.get("memberAccountId").toString(),
			// genericValue.get("accountNo")+" - "+genericValue.getString("accountName"));
			bdProductChargeAmount = genericValue.getBigDecimal("fixedAmount");
		}

		log.info(" CCCCCCCCCCCCCCCCCCCCC Card Amount is "
				+ bdProductChargeAmount);
		return bdProductChargeAmount;
	}

	/**
	 * @author Japheth Odonya @when Nov 5, 2014 8:39:37 PM Determines Account
	 *         Minimum Balance from Configuration
	 * */
	private static BigDecimal getAccountMinimumBalance(Long accountProductId) {

		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		GenericValue accountProduct = null;

		// SaccoProduct
		try {
			accountProduct = delegator
					.findOne("AccountProduct", UtilMisc.toMap(
							"accountProductId", accountProductId), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		BigDecimal bdMinimumBalance = BigDecimal.ZERO;

		bdMinimumBalance = accountProduct.getBigDecimal("minBalanceAmt");

		log.info(" MMMMMMMMMMMMMMMMMM Minimum Balance is " + bdMinimumBalance);
		return bdMinimumBalance;
	}

	public static ATMStatus getATMAccount(String cardNumber) {
		String status = "";

		GenericValue cardApplication = getMemberATMApplication(cardNumber);

		Long cardStatusId = null;
		cardStatusId = ATMManagementServices.getCardStatus("ACTIVE");
		if (cardApplication == null) {
			status = "NOTREGISTERED";
		} else if (cardApplication.getLong("cardStatusId").equals(cardStatusId)) {
			status = "SUCCESS";
		} else {
			status = "NOTACTIVE";
		}
		// ATMManagementServices.get
		ATMStatus atmStatus = new ATMStatus();
		atmStatus.setStatus(status);
		atmStatus.setCardStatusId(cardApplication.getLong("cardStatusId"));
		atmStatus.setCardStatus(ATMManagementServices
				.getCardStatusName(cardStatusId));
		atmStatus.setCardApplication(cardApplication);

		return atmStatus;
	}

	/***
	 * Get ATM Application given a Card Number
	 * 
	 * Either Returns an Card Application or Null if none exists
	 * */
	public static GenericValue getMemberATMApplication(String cardNumber) {
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		List<GenericValue> cardApplicationELI = null;
		EntityConditionList<EntityExpr> cardApplicationConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"cardNumber", EntityOperator.EQUALS, cardNumber)),
						EntityOperator.AND);

		try {
			cardApplicationELI = delegator.findList("CardApplication",
					cardApplicationConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}
		GenericValue cardApplication = null;

		for (GenericValue genericValue : cardApplicationELI) {
			cardApplication = genericValue;
		}
		return cardApplication;
	}

	/****
	 * @author Japheth Odonya @when Jun 11, 2015 8:34:37 PM
	 * 
	 *         Check that a member already has an ATM Card in the system
	 * 
	 * */
	public static Boolean alreadyHasAnATMCard(Long partyId) {
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		List<GenericValue> cardApplicationELI = null;
		EntityConditionList<EntityExpr> cardApplicationConditions = EntityCondition
				.makeCondition(UtilMisc.toList(EntityCondition.makeCondition(
						"partyId", EntityOperator.EQUALS, partyId)),
						EntityOperator.AND);

		try {
			cardApplicationELI = delegator.findList("CardApplication",
					cardApplicationConditions, null, null, null, false);

		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}

		if ((cardApplicationELI != null) && (cardApplicationELI.size() > 0))
			return true;

		return false;
	}

	public static String processCardSubmission(Long cardApplicationId,
			Map<String, String> userLogin) {

		// Member must have money in the saving account
		String statusName = "APPLIED";

		GenericValue cardStatus = getCardStatusEntity(statusName);
		Long productChargeId = cardStatus.getLong("productChargeId");

		GenericValue productCharge = getProductChargeEntity(productChargeId);

		BigDecimal bdChargeAmount = productCharge.getBigDecimal("fixedAmount");

		GenericValue cardApplication = getCardApplication(cardApplicationId);

		Long memberAccountId = cardApplication.getLong("memberAccountId");

		// BigDecimal bdMinimumBalance =
		// LoanUtilities.getMinimumBalance(memberAccountId);
		GenericValue accountProduct = LoanUtilities
				.getAccountProductGivenMemberAccountId(memberAccountId);

		// BigDecimal bdTotalAmount = bdChargeAmount.add(bdMinimumBalance);

		BigDecimal bdBalance = AccHolderTransactionServices
				.getAvailableBalanceVer3(memberAccountId.toString());

		// if (bdBalance)
		BigDecimal bdExciseDuty = BigDecimal.ZERO;
		bdExciseDuty = bdChargeAmount.multiply(EXCISEDUTYPERCENTAGE).divide(
				new BigDecimal(100), 4, RoundingMode.HALF_UP);

		BigDecimal bdTotalCharge = bdChargeAmount.add(bdExciseDuty);

		// bdTotalCharge
		// bdChargeAmount
		// bdExciseDuty

		// glAccountId
		// chargeAccountId
		// exciseDutyAccountId

		if (bdBalance.compareTo(bdTotalCharge) == -1) {
			return " Member Balance is " + bdBalance
					+ " while the charge for Card application is "
					+ bdTotalCharge + " (Application Fee + Commission) "
					+ ", a member must have enough money in his account";
		} else {
			// Post the ATM Application Fee and the Commission

			String chargeAccountId = productCharge.getString("chargeAccountId");
			String exciseDutyAccountId = accountProduct
					.getString("exciseDutyAccountId");
			String glAccountId = accountProduct.getString("glAccountId");

			String branchId = "Company";

			if (!LoanUtilities.organizationAccountMapped(chargeAccountId,
					branchId)) {
				return "Please make sure the Charge Account is mapped to all branches";
			}

			if (!LoanUtilities.organizationAccountMapped(exciseDutyAccountId,
					branchId)) {
				return "Please make sure the Charge Account is mapped to all branches";
			}

			String acctgTransId = AccHolderTransactionServices
					.postCardApplicationFee(userLogin, bdTotalCharge,
							bdChargeAmount, bdExciseDuty, glAccountId,
							chargeAccountId, exciseDutyAccountId);

			// Make an entry in the MPA
			//AccHolderTransactionServices.pos
			//for the Charge
			AccHolderTransactionServices.cashDepositVersion4(bdChargeAmount, memberAccountId, userLogin, "CARDAPPLICATIONCHARGES", acctgTransId);
			//for the excise duty
			AccHolderTransactionServices.cashDepositVersion4(bdExciseDuty, memberAccountId, userLogin, "EXCISEDUTY", acctgTransId);
		}

		return "success";
	}

	private static GenericValue getCardApplication(Long cardApplicationId) {
		GenericValue cardApplication = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			cardApplication = delegator.findOne("CardApplication",
					UtilMisc.toMap("cardApplicationId", cardApplicationId),
					false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		return cardApplication;
	}

	/****
	 * @author Japheth Odonya @when Jun 11, 2015 10:18:26 PM
	 * */
	private static GenericValue getProductChargeEntity(Long productChargeId) {
		GenericValue productCharge = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			productCharge = delegator.findOne(
					"ProductCharge",
					UtilMisc.toMap("productChargeId",
							Long.valueOf(productChargeId)), false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		return productCharge;

	}
	
	
	public static String validateCardReceive(Long cardApplicationId,
			Map<String, String> userLogin) {

		// Member must have money in the saving account
		String statusName = "APPLIED";
		
		//GenericValue cardStatus = getCardStatusEntity(statusName);
		Long cardStatusId = getCardStatus(statusName);


		GenericValue cardApplication = getCardApplication(cardApplicationId);
		
		if (cardStatusId != cardApplication.getLong("cardStatusId"))
			return "Can only receive applied cards, please make sure you have applied for this card first";
		
		return "success";
	}
	
	
	public static String validateCardActivate(Long cardApplicationId,
			Map<String, String> userLogin) {

		// Member must have money in the saving account
		String statusNameNew = "NEW";
		String statusNameApplied = "APPLIED";
		String statusNameReceived = "RECEIVED";
		String statusActive = "ACTIVE";
		
		//GenericValue cardStatus = getCardStatusEntity(statusName);
		Long cardStatusNewId = getCardStatus(statusNameNew);
		Long cardStatusAppliedId = getCardStatus(statusNameApplied);
		Long cardStatusReceivedId = getCardStatus(statusNameReceived);
		Long cardStatusActiveId = getCardStatus(statusActive);


		GenericValue cardApplication = getCardApplication(cardApplicationId);
		
		if (cardStatusActiveId == cardApplication.getLong("cardStatusId"))
			return "The CARD is already activated !";

		
		if (cardStatusNewId == cardApplication.getLong("cardStatusId"))
			return "Cannot activate a card in the New state, a cards to be applied, received and issued before being Activated";

		if (cardStatusAppliedId == cardApplication.getLong("cardStatusId"))
			return "Cannot activate a card in the APPLIED state, a cards to be applied, received and issued before being Activated";


		if (cardStatusReceivedId == cardApplication.getLong("cardStatusId"))
			return "Cannot activate a card in the RECEIVED state, a cards to be applied, received and issued before being Activated";

		return "success";
	}
	
	/***
	 * @author Japheth Odonya  @when Jun 23, 2015 10:17:19 PM
	 * 
	 * The actionName could be
	 * NEW
	 * APPLIED
	 * RECEIVED
	 * ISSUED
	 * ACTIVE
	 * RENEW
	 * DEACTIVATED
	 * REPLACE
	 * RECEIVEPIN
	 * ISSUEPIN
	 * APPROVED
	 * CLOSED
	 * */
	public static String actionAlreadyDoneOnCard(Long cardApplicationId, String actionName){
		String doneStatus = "notdone";
		
		Long cardStatusId = LoanUtilities.getCardStatusId(actionName);
		
		
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		List<GenericValue> cardLogELI = new ArrayList<GenericValue>();
		
		EntityConditionList<EntityExpr> cardLogConditions = EntityCondition
				.makeCondition(
						UtilMisc.toList(EntityCondition.makeCondition(
								"cardApplicationId", EntityOperator.EQUALS,
								cardApplicationId),
								
								EntityCondition.makeCondition(
										"cardStatusId", EntityOperator.EQUALS,
										cardStatusId)
								
								
								), EntityOperator.AND);
		
		try {
			cardLogELI = delegator.findList("CardLog",
					cardLogConditions, null, null,
					null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Boolean done = false;
		if ((cardLogELI != null) && (cardLogELI.size() > 0)){
			done = true;
		}
		
		if (done == false)
			return doneStatus;
		
		/*****
		 * 
		 * 	 * NEW
	 * APPLIED
	 * RECEIVED
	 * ISSUED
	 * 
	 * ACTIVE
	 * RENEW
	 * DEACTIVATED
	 * 
	 * REPLACE
	 * 
	 * RECEIVEPIN
	 * ISSUEPIN
	 * APPROVED
	 * CLOSED
		 * 
		 * */
		if (actionName.equals("APPLIED"))
		{
			doneStatus = " Card already applied for, cannot apply again !";
		}
		
		
		if (actionName.equals("RECEIVED"))
		{
			doneStatus = " Card already received, you cannot receive the card again !";
		}
		
		if (actionName.equals("ISSUED"))
		{
			doneStatus = " Card already issued, you cannot issue the card again !";
		}
		
		if (actionName.equals("RECEIVEPIN"))
		{
			doneStatus = " PIN already received, cannot receive PIN again !";
		}
		if (actionName.equals("ISSUEPIN"))
		{
			doneStatus = " PIN already issued, cannot issue PIN again !";
		}
		
		if (actionName.equals("APPROVED"))
		{
			doneStatus = " Card already approved, cannot approve again !";
		}
		
		if (actionName.equals("ACTIVE")){
			
			Long currentCardStatusId = LoanUtilities.getCurrentCardStatusId(cardApplicationId);
			
			Long activeCardStatusId = LoanUtilities.getCardStatusId("ACTIVE");
			
			if (currentCardStatusId.equals(activeCardStatusId))
				return "CARD is already ACTIVE cannot activate an active CARD!";
			
//			Long deactivatedCardStatusId = LoanUtilities.getCardStatusId("DEACTIVATED");
//			if (!deactivatedCardStatusId.equals(currentCardStatusId)){
//				return "Can only acti";
//			}
			//Long cardPreviousStatusId = LoanUtilities.getCardPreviousStatusId
			
			// CARD NOT ACTIVE
			//Previous stage must be ISSUED or DEACTIVATED
			//
			
		}
		
		if (actionName.equals("DEACTIVATED")){
			//CARD not DEACTIVATED
			//Previous status is ACTIVE
			
			Long currentCardStatusId = LoanUtilities.getCurrentCardStatusId(cardApplicationId);
			
			Long deactivatedCardStatusId = LoanUtilities.getCardStatusId("DEACTIVATED");
			
			if (currentCardStatusId.equals(deactivatedCardStatusId))
				return "Cannot deactivate a deactivated CARD";
		}
		
		
		return doneStatus;
	}

}
