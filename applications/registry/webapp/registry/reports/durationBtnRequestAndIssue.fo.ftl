<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<#escape x as x?xml>
    <#if activities?has_content>

    <#-- REPORT TITLE -->
    <fo:block font-size="18pt" font-weight="bold" text-align="center">
        CHAI SACCO
    </fo:block>
    <fo:block font-size="12pt" text-align="center"  font-weight="bold" >
         DURATION BETWEEN FILE REQUEST AND ISSUANCE REPORT
    </fo:block>
    <fo:block><fo:leader/></fo:block>

<#if activities?has_content>
    <#-- REPORT BODY -->
    <fo:block space-after.optimum="10pt" font-size="10pt">
        <fo:table table-layout="fixed" width="100%">
            <fo:table-column column-width="70pt"/>
            <fo:table-column column-width="100pt"/>
            <fo:table-column column-width="100pt"/>
            <fo:table-column column-width="100pt"/>
            <fo:table-column column-width="70pt"/>
            <fo:table-column column-width="120pt"/>
            <fo:table-header>
                <fo:table-row font-weight="bold">
                    <fo:table-cell padding="2pt" background-color="#D4D0C8" border="1pt solid" border-width=".1mm">
                        <fo:block text-align="left">File Owner</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2pt" background-color="#D4D0C8" border="1pt solid" border-width=".1mm">
                        <fo:block text-align="left">File Issued By</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2pt" background-color="#D4D0C8" border="1pt solid" border-width=".1mm">
                        <fo:block text-align="left">File Issued To</fo:block>
                    </fo:table-cell>
                    <fo:table-cell padding="2pt" background-color="#D4D0C8" border="1pt solid" border-width=".1mm">
                        <fo:block text-align="left">Request Reason</fo:block>
                    </fo:table-cell>
                    
                    
                     <fo:table-cell padding="2pt" background-color="#D4D0C8" border="1pt solid" border-width=".1mm">
                        <fo:block text-align="left">Date Issued</fo:block>
                    </fo:table-cell>
                    
                    <fo:table-cell padding="2pt" background-color="#D4D0C8" border="1pt solid" border-width=".1mm">
                        <fo:block text-align="left">Duration From Request (Given In Days)</fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-header>
            <fo:table-body>

                  <#list activities as activity>
                   <#if activity.carriedBy?has_content>
                        <#assign carriedBy = delegator.findOne("Person", {"partyId" : activity.carriedBy}, false)/>
                    </#if>
                    
                     <#if activity.partyId?has_content>
                    	<#assign memberPartyId = activity.partyId?number />
                        <#assign member = delegator.findOne("Member", {"partyId" : memberPartyId?long}, false)/>
                    </#if>
                    
                    <#if activity.actionBy?has_content>
                        <#assign actionBy = delegator.findOne("Person", {"partyId" : activity.actionBy}, false)/>
                    </#if>
                    <#if activity.Reason?has_content>
                        <#assign reason = delegator.findOne("RegistryFileActivity", {"activityId" : activity.Reason}, false)/>
                    </#if>
                  
                    <#if activity.currentPossesser?has_content>
                        <#assign currentPossesser = delegator.findOne("Person", {"partyId" : activity.currentPossesser}, false)/>
                    </#if>
                     <fo:table-row>
                        <fo:table-cell padding="2pt" border="1pt solid" border-width=".1mm">
                            <#if member?has_content>
                                  <fo:block>${member.firstName?if_exists} ${member.lastName?if_exists}</fo:block>
                            <#else>
                                <fo:block>Not Defined</fo:block>
                            </#if>
                        </fo:table-cell>
                        <fo:table-cell padding="2pt" border="1pt solid" border-width=".1mm">
                            <#if actionBy?has_content>
                                <fo:block>${actionBy.firstName?if_exists} ${actionBy.lastName?if_exists}</fo:block>
                            <#else>
                                <fo:block>Not Defined</fo:block>
                            </#if>
                        </fo:table-cell>
                        <fo:table-cell padding="2pt" border="1pt solid" border-width=".1mm">
                            <#if activity.currentPossesser?if_exists == "REGISTRY">
                                <fo:block>${activity.currentPossesser?if_exists}</fo:block>
                            <#else>
                                 <fo:block>${currentPossesser.firstName?if_exists} ${currentPossesser.lastName?if_exists}</fo:block>
                            </#if>
                        </fo:table-cell>
                        
                         
                         <fo:table-cell padding="2pt" border="1pt solid" border-width=".1mm">
                            <#if reason?has_content>
                                <fo:block>${reason.activity?if_exists}</fo:block>
                            <#else>
                                <fo:block>Not Defined</fo:block>
                            </#if>
                        </fo:table-cell>
                        
                         <fo:table-cell padding="2pt" border="1pt solid" border-width=".1mm">
                            <fo:block>${activity.actionDate?if_exists}</fo:block>
                        </fo:table-cell>
                        
                        <fo:table-cell padding="2pt" border="1pt solid" border-width=".1mm">
                            <fo:block>${activity.interActivityDuration?if_exists}</fo:block>
                        </fo:table-cell>
                        
                     </fo:table-row>
                  </#list>

            </fo:table-body>
        </fo:table>
    </fo:block>
    <#else>
     <fo:block space-after.optimum="10pt" >
        <fo:block text-align="center" font-size="14pt">Nothing To Show</fo:block>
    </fo:block>
  </#if>
    <#else>
        <fo:block text-align="center">No Employees Found With that ID</fo:block>
    </#if>
</#escape>

