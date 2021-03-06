package org.ofbiz.party.party;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ofbiz.accountholdertransactions.LoanUtilities;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactoryImpl;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;

//org.ofbiz.party.party.SaccoUtility.getNextSequenc()

//org.ofbiz.party.party.getMemberStatusID.SaccoUtility(name)
public class SaccoUtility {
	
	public static Log log = LogFactory.getLog(SaccoUtility.class);
	
	public static Long getNextSequenc(String sequenceName){
		Long nextId = 0L;
		
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		nextId = delegator.getNextSeqIdLong(sequenceName);
		
		return nextId;
	}
	
	public static Long getNextPartySequence(String sequenceName){
		String nextId = "";
		
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		nextId = delegator.getNextSeqId(sequenceName);
		
		return Long.valueOf(nextId);
	}
	
	public static String getNextStringSequence(String sequenceName){
		String nextId = "";
		
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		nextId = delegator.getNextSeqId(sequenceName);
		
		return nextId;
	}
	
	//Get memberStatusId
	public static Long getMemberStatusID(String name) {
		List<GenericValue> memberStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			memberStatusELI = delegator.findList("MemberStatus",
					EntityCondition.makeCondition("name",
							name), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Long memberStatusId = 0L;
		 for (GenericValue genericValue : memberStatusELI) {
			 memberStatusId = genericValue.getLong("memberStatusId");
		 }

		return memberStatusId;
	}
	
	
	/****
	 * Create MSacco and ATM based on the member record is the member needs MSacco or ATM
	 * */
	public static String createMSaccoATMRecords(String memberNumber, Map<String, String> userLogin){
		
		GenericValue member = LoanUtilities.getMemberGivenMemberNumber(memberNumber);
		
		//atmApplication
		//msaccoApplication
		
		Boolean atmExists = false;
		Boolean msaccoExists = false;
		
		Long partyId = member.getLong("partyId");
		
		atmExists = LoanUtilities.checkATMExists(partyId);
		msaccoExists = LoanUtilities.checkMsaccoExists(partyId);
		
		Boolean hasSAvingsAccount = false;
		
		hasSAvingsAccount = LoanUtilities.memberHasSavingsAccount(partyId);
		
		if (!hasSAvingsAccount)
			return "doesnothavesavingsaccount";
		
		
		if (!atmExists){
		if (member.getString("atmApplication").equals("Y")){
			log.info("SSSSSSSSSSSSS creating ATM application for the member no "+memberNumber);
			
			//Add ATM Application for a member
			LoanUtilities.addNewATMApplication(partyId, userLogin);
		}
		}

		if (!msaccoExists){
		if (member.getString("msaccoApplication").equals("Y")){
			log.info("SSSSSSSSSSSSS creating MSacco application for the member no "+memberNumber);
			
			//Add MSacco Application for member
			LoanUtilities.addNewMSaccoApplication(partyId, userLogin);
		}
		}
		
		return "success";
	}
	
	/**
	 * getMemberStatusActiveId
		getMemberStatusNewId
	 * 
	 * */
	public static Long getMemberStatusActiveId() {
		List<GenericValue> memberStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			memberStatusELI = delegator.findList("MemberStatus",
					EntityCondition.makeCondition("name",
							"ACTIVE"), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Long memberStatusId = 0L;
		 for (GenericValue genericValue : memberStatusELI) {
			 memberStatusId = genericValue.getLong("memberStatusId");
		 }

		return memberStatusId;
	}
	
	public static Long getMemberStatusNewId() {
		List<GenericValue> memberStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			memberStatusELI = delegator.findList("MemberStatus",
					EntityCondition.makeCondition("name",
							"NEW"), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Long memberStatusId = 0L;
		 for (GenericValue genericValue : memberStatusELI) {
			 memberStatusId = genericValue.getLong("memberStatusId");
		 }

		return memberStatusId;
	}
	
	/***
	 * Get Loan Status
	 * */
	
	public static Long getLoanStatusId(String name) {
		List<GenericValue> loanStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			loanStatusELI = delegator.findList("LoanStatus",
					EntityCondition.makeCondition("name",
							name), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Long loanStatusId = 0L;
		 for (GenericValue genericValue : loanStatusELI) {
			 loanStatusId = genericValue.getLong("loanStatusId");
		 }
		 
		 String statusIdString = String.valueOf(loanStatusId);
		 statusIdString = statusIdString.replaceAll(",", "");
		 loanStatusId = Long.valueOf(statusIdString);
		return loanStatusId;
	}
	
	public static String replaceString(String name) {
		name = name.replaceAll(",", "");

		return name;
	}

	public static Integer getBusinessMemberCount() {
		Long memberTypeId = getMemberTypeId("BUSINESS");
		
		//Get the count of member of type Business
		
		List<GenericValue> memberELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			memberELI = delegator.findList("Member",
					EntityCondition.makeCondition("memberTypeId",
							memberTypeId), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		
		
		return memberELI.size();
	}
	
	
	public static Long getMemberTypeId(String name) {
		List<GenericValue> memberTypeELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			memberTypeELI = delegator.findList("MemberType",
					EntityCondition.makeCondition("name",
							name), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Long memberTypeId = 0L;
		 for (GenericValue genericValue : memberTypeELI) {
			 memberTypeId = genericValue.getLong("memberTypeId");
		 }
		return memberTypeId;
	}
	
	
	//value - Mother columnName name, entityName Relationship
	public static Boolean itemExists(String value, String columnName, String entityName) {
		List<GenericValue> entityListELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		
		log.info("##########VVVVVVVVVV Value :: "+value+" Column Name "+columnName+" Entity Name "+entityName);
		
		try {
			entityListELI = delegator.findList(entityName,
					EntityCondition.makeCondition(columnName,
							value), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		log.info(" SSSSSSSSSSSS The Size is "+entityListELI.size());
		
		if (entityListELI.size() > 0)
			return true;

		return false;
	}
	
	public static Boolean isInExcess(Long memberNomineeId, BigDecimal percentage, String fieldName, String entityName, Long memberId){
	
		//Get the total already saved 
		BigDecimal bdTotalAssigned = BigDecimal.ZERO;
		
		List<GenericValue> memberNomineeELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		
		//log.info("##########VVVVVVVVVV Value :: "+value+" Column Name "+columnName+" Entity Name "+entityName);
		
		try {
			memberNomineeELI = delegator.findList(entityName,
					EntityCondition.makeCondition("partyId",
							memberId), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		for (GenericValue genericValue : memberNomineeELI) {
			bdTotalAssigned = bdTotalAssigned.add(genericValue.getBigDecimal("percentage"));
		}

		if (memberNomineeId == null){
			bdTotalAssigned = bdTotalAssigned.add(percentage);
		} else{
			BigDecimal currentPercentage = getCurrentNomineePercentage(memberNomineeId);
			bdTotalAssigned = bdTotalAssigned.add(percentage);
			bdTotalAssigned = bdTotalAssigned.subtract(currentPercentage);
		}
		//total already saved plus percentage assigned must be less or equal to 100
		
		if (bdTotalAssigned.compareTo(new BigDecimal(100)) == 1)
			return true; //In excess
		
		return false;
	}

	private static BigDecimal getCurrentNomineePercentage(Long memberNomineeId) {
		List<GenericValue> memberNomineeELI = null; // =
		BigDecimal bdNomineePercentage = BigDecimal.ZERO;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		
		//log.info("##########VVVVVVVVVV Value :: "+value+" Column Name "+columnName+" Entity Name "+entityName);
		
		try {
			memberNomineeELI = delegator.findList("MemberNominee",
					EntityCondition.makeCondition("memberNomineeId",
							memberNomineeId), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		for (GenericValue genericValue : memberNomineeELI) {
			bdNomineePercentage = bdNomineePercentage.add(genericValue.getBigDecimal("percentage"));
		}
		
		
		return bdNomineePercentage;
	}
	
	public static Date getJoinDate(Long memberId)
	{
		Date joinDate = null;
		GenericValue member = null;
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			member = delegator.findOne("Member",
					UtilMisc.toMap("partyId", memberId), false);
		} catch (GenericEntityException e2) {
			e2.printStackTrace();
		}
		
		if (member != null)
			joinDate = member.getDate("joinDate");
		
		
		log.info(" The OLD DATE IS IIIIIIIIIIIIIIIIIIIII "+joinDate);
		return joinDate;
	}
	
	
	/****
	 * @author Japheth Odonya  @when Jun 11, 2015 8:24:29 PM
	 * For the CARDS
	 * 
	 * */
	public static Long getCardStatusId(String name){
		List<GenericValue> cardStatusELI = null; // =
		Delegator delegator = DelegatorFactoryImpl.getDelegator(null);
		try {
			cardStatusELI = delegator.findList("CardStatus",
					EntityCondition.makeCondition("name",
							name), null, null, null, false);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		
		Long cardStatusId = 0L;
		 for (GenericValue genericValue : cardStatusELI) {
			 cardStatusId = genericValue.getLong("cardStatusId");
		 }
		 
		return cardStatusId;
	}
	

}
