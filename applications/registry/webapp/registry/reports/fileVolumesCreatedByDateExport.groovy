
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionBuilder;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.entity.util.EntityFindOptions;
import java.text.SimpleDateFormat; 



startDate = parameters.startDate
endDate = parameters.endDate
fileVolumesCreatedByDatelist = [];
dateStartDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(startDate);
dateEndDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(endDate);

 sqlStartDate = new java.sql.Timestamp(dateStartDate.getTime());
 sqlEndDate = new java.sql.Timestamp(dateEndDate.getTime());

exprBldr = new org.ofbiz.entity.condition.EntityConditionBuilder()





	expr = exprBldr.AND() {
			GREATER_THAN_EQUAL_TO(createdStamp: sqlStartDate)
			LESS_THAN_EQUAL_TO(createdStamp: sqlEndDate)
		}
EntityFindOptions findOptions = new EntityFindOptions();
findOptions.setMaxRows(100);
fileVolumesCreatedByDate = delegator.findList("RegistryFileVolume", expr, null, ["createdStamp ASC"], findOptions, false)
	
  
 fileVolumesCreatedByDate.eachWithIndex { fileVolumesCreatedByDateItem, index ->
 party = fileVolumesCreatedByDateItem.partyId;
 partylong = party.toLong();
 member = delegator.findOne("Member", [partyId : partylong], false);
 
 payRollNumber  =  "${member.payrollNumber}";
 memberNumber  =  "${member.memberNumber}";
 fileOwner = "${member.firstName}  ${member.lastName}";
 identifier = fileVolumesCreatedByDateItem.volumeIdentifier;
 status = fileVolumesCreatedByDateItem.volumeStatus;
 
  timein  = fileVolumesCreatedByDateItem.createdStamp;
 dateStringin = timein.format("yyyy-MMM-dd HH:mm:ss a")
 dateCreated = dateStringin;
 
 
 
 
 fileVolumesCreatedByDatelist.add([fileOwner :fileOwner,payRollNumber : payRollNumber, memberNumber : memberNumber, identifier :identifier, status : status, dateCreated : dateCreated]);
 }
 
 
context.fileVolumesCreatedByDatelist = fileVolumesCreatedByDatelist;




