/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.referencedemodata;

import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleException;
import org.openmrs.module.emrapi.utils.MetadataUtil;
import org.openmrs.module.providermanagement.ProviderRole;
import org.openmrs.module.providermanagement.api.ProviderManagementService;
import org.openmrs.util.PrivilegeConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class ReferenceDemoDataActivator extends BaseModuleActivator {

	protected Log log = LogFactory.getLog(getClass());
	
	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		log.info("Reference Demo Data Module refreshed");
	}
	
	/**
	 * @see ModuleActivator#started()
	 * @should install the metadata package on startup
	 * @should link the admin account to unknown provider
	 */
	public void started() {
		installMDSPackages();
		//This should probably be removed once a test user is added to demo data
		//See https://tickets.openmrs.org/browse/RA-184
		linkAdminAccountToAProviderIfNecessary();
		configureConceptsIfNecessary();
		setRequiredGlobalProperties();
		setupUsersAndProviders();
	}
	
	private void installMDSPackages() {
		try {
			MetadataUtil.setupStandardMetadata(getClass().getClassLoader(), "org/openmrs/module/referencedemodata/packages.xml");
		}
		catch (Exception e) {
			throw new ModuleException("Failed to load reference demo data MDS packages", e);
		}
		
		log.info("Reference Demo Data Module started");
	}
	
	private void linkAdminAccountToAProviderIfNecessary() {
		try {
			//If unknown provider isn't yet linked to admin, then do it
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_PROVIDERS);
			Context.addProxyPrivilege(PrivilegeConstants.VIEW_PERSONS);
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
			ProviderService ps = Context.getProviderService();
			Person adminPerson = Context.getPersonService().getPerson(1);
			Collection<Provider> possibleProvider = ps.getProvidersByPerson(adminPerson);
			if (possibleProvider.size() == 0) {
				List<Provider> providers = ps.getAllProviders(false);

				Provider provider;
				if (providers.size() == 0) {
					provider = new Provider();
					provider.setIdentifier("admin");
				} else {
					provider = providers.get(0);
				}
				provider.setPerson(adminPerson);
				ps.saveProvider(provider);
			}
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_PROVIDERS);
			Context.removeProxyPrivilege(PrivilegeConstants.VIEW_PERSONS);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_PROVIDERS);
		}
	}

	private void setupUsersAndProviders() {
		Person clerkPerson = setupPerson(ReferenceDemoDataConstants.CLERK_PERSON_UUID, "M", "John", "Smith");
		Person nursePerson = setupPerson(ReferenceDemoDataConstants.NURSE_PERSON_UUID, "F", "Jane", "Smith");
		Person doctorPerson = setupPerson(ReferenceDemoDataConstants.DOCTOR_PERSON_UUID, "M", "Jake", "Smith");

		UserService userService = Context.getUserService();
		Role clerkRole = userService.getRoleByUuid(ReferenceDemoDataConstants.CLERK_ROLE_UUID);
		Role nurseRole = userService.getRoleByUuid(ReferenceDemoDataConstants.NURSE_ROLE_UUID);
		Role doctorRole = userService.getRoleByUuid(ReferenceDemoDataConstants.DOCTOR_ROLE_UUID);

		setupUser(ReferenceDemoDataConstants.CLERK_USER_UUID, "clerk", clerkPerson, "Clerk123", clerkRole);
		setupUser(ReferenceDemoDataConstants.NURSE_USER_UUID, "nurse", nursePerson, "Nurse123", nurseRole);
		setupUser(ReferenceDemoDataConstants.DOCTOR_USER_UUID, "doctor", doctorPerson, "Doctor123", doctorRole);

		ProviderManagementService providerManagementService = Context.getService(ProviderManagementService.class);

		ProviderRole clerkProviderRole = providerManagementService.getProviderRoleByUuid(ReferenceDemoDataConstants.CLERK_PROVIDER_ROLE_UUID);
		ProviderRole nurseProviderRole = providerManagementService.getProviderRoleByUuid(ReferenceDemoDataConstants.NURSE_PROVIDER_ROLE_UUID);
		ProviderRole doctorProviderRole = providerManagementService.getProviderRoleByUuid(ReferenceDemoDataConstants.DOCTOR_PROVIDER_ROLE_UUID);

		providerManagementService.assignProviderRoleToPerson(clerkPerson, clerkProviderRole, "clerk");
		providerManagementService.assignProviderRoleToPerson(nursePerson, nurseProviderRole, "nurse");
		providerManagementService.assignProviderRoleToPerson(doctorPerson, doctorProviderRole, "doctor");

		//It's added temporarily until we figure out which privileges/roles should be included
		Role adminRole = userService.getRole("System Developer");
		clerkRole.getInheritedRoles().add(adminRole);
		nurseRole.getInheritedRoles().add(adminRole);
		doctorRole.getInheritedRoles().add(adminRole);
		userService.saveRole(clerkRole);
		userService.saveRole(nurseRole);
		userService.saveRole(doctorRole);
	}

	private User setupUser(String uuid, String username, Person person, String password, Role... roles) {
		UserService userService = Context.getUserService();

		User user = userService.getUserByUuid(uuid);
		if (user == null) {
			user = new User();
			user.setUuid(uuid);
			user.setRoles(new HashSet<Role>());
		}
		user.setUsername(username);
		user.setPerson(person);

		user.getRoles().clear();
		user.getRoles().addAll(Arrays.asList(roles));

		user = userService.saveUser(user, password);

		return user;
	}

	private Person setupPerson(String uuid, String gender, String givenName, String familyName) {
		PersonService personService = Context.getPersonService();

		Person person = personService.getPersonByUuid(uuid);
		if (person == null) {
			person = new Person();
			person.setUuid(uuid);
		}
		person.setGender(gender);

		PersonName name = person.getPersonName();
		if (name == null) {
			name = new PersonName();
			person.addName(name);
		}
		name.setGivenName(givenName);
		name.setFamilyName(familyName);

		return person;
	}

	private void configureConceptsIfNecessary() {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
			ConceptService cs = Context.getConceptService();
			ConceptMapType sameAsMapType = cs.getConceptMapTypeByUuid("35543629-7d8c-11e1-909d-c80aa9edcf4e");
			//Not bothering to check for null because i know demo data should have these
			Concept visitDiagnosisConcept = cs.getConceptByUuid("159947AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
			
			//diagnosis concept is required to be a set member of visit diagnosis concept
			Concept diagnosisConcept = cs.getConceptByUuid("226ed7ad-b776-4b99-966d-fd818d3302c2");
			if (visitDiagnosisConcept != null && !visitDiagnosisConcept.getSetMembers().contains(diagnosisConcept)) {
				visitDiagnosisConcept.addSetMember(diagnosisConcept);
				cs.saveConcept(visitDiagnosisConcept);
			}
			
			//Map<conceputUuid, Code>
			Map<String, String> emrSourceConceptMappings = new HashMap<String, String>();
			emrSourceConceptMappings.put("159947AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Diagnosis Concept Set");
			emrSourceConceptMappings.put("161602AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Non-Coded Diagnosis");
			emrSourceConceptMappings.put("159946AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Diagnosis Order");
			emrSourceConceptMappings.put("159394AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Diagnosis Certainty");
			emrSourceConceptMappings.put("159395AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Consult Free Text Comments");
			
			Map<String, String> pihSourceConceptMappings = new HashMap<String, String>();
			//Apparently this concept's uuid is different for different installations
			final String conceptNameAndMappingCode = "RETURN VISIT DATE";
			Concept returnVisitConcept = cs.getConceptByName(conceptNameAndMappingCode);
			if (returnVisitConcept != null) {
				pihSourceConceptMappings.put(returnVisitConcept.getUuid(), conceptNameAndMappingCode);
			}
			
			Map<String, Map<String, String>> sourceConceptMappingsMap = new HashMap<String, Map<String, String>>();
			sourceConceptMappingsMap.put("org.openmrs.module.emr", emrSourceConceptMappings);
			sourceConceptMappingsMap.put("PIH", pihSourceConceptMappings);
			
			for (Map.Entry<String, Map<String, String>> sourceAndMappings : sourceConceptMappingsMap.entrySet()) {
				ConceptSource source = cs.getConceptSourceByName(sourceAndMappings.getKey());
				if (source != null) {
					for (Map.Entry<String, String> entry : sourceAndMappings.getValue().entrySet()) {
						boolean hasMapping = cs.getConceptByMapping(entry.getValue(), source.getName(), false) != null;
						if (!hasMapping) {
							Concept c = cs.getConceptByUuid(entry.getKey());
                            ConceptReferenceTerm term = cs.getConceptReferenceTermByCode(entry.getValue(), source);
                            if (term == null) {
                                term = new ConceptReferenceTerm(source, entry.getValue(), null);
                                cs.saveConceptReferenceTerm(term);
                            }
							c.addConceptMapping(new ConceptMap(term, sameAsMapType));
							cs.saveConcept(c);
						}
					}
				}
			}
			
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
		}
	}
	
	private void setRequiredGlobalProperties() {
		AdministrationService as = Context.getAdministrationService();
		Map<String, String> propertyValueMap = new HashMap<String, String>();
		//Add more GPs here
		propertyValueMap.put("registrationcore.identifierSourceId", "1");
		
		for (Map.Entry<String, String> entry : propertyValueMap.entrySet()) {
			if (StringUtils.isBlank(as.getGlobalProperty(entry.getKey()))) {
				GlobalProperty gp = as.getGlobalPropertyObject(entry.getKey());
				if (gp == null) {
					gp = new GlobalProperty();
					gp.setProperty(entry.getKey());
				}
				gp.setPropertyValue(entry.getValue());
				as.saveGlobalProperty(gp);
			}
		}

		as.saveGlobalProperty(new GlobalProperty("scheduler.username", "admin"));
		as.saveGlobalProperty(new GlobalProperty("scheduler.password", "Admin123"));
	}
}
