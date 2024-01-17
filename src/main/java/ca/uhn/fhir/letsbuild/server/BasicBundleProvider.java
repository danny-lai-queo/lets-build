package ca.uhn.fhir.letsbuild.server;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.provider.HashMapResourceProvider;

public class BasicBundleProvider extends SimpleBundleProvider {
    private FhirContext ctx;
    private HashMapResourceProvider<Patient> patientProvider;
    private HashMapResourceProvider<Observation> observationProvider;

    private static final Logger ourLog = LoggerFactory.getLogger(SimpleBundleProvider.class);

    public BasicBundleProvider(FhirContext ctx, 
        IResourceProvider patProvider, IResourceProvider obsProvider) {
        super();
        this.ctx = ctx;
        try {
            this.patientProvider = (HashMapResourceProvider<Patient>)patProvider;
            this.observationProvider = (HashMapResourceProvider<Observation>)obsProvider;
        } catch (Exception e) {
            ourLog.info("failed to cast", e);
            throw e;
        }

    }

    @Transaction
    public Bundle transaction(@TransactionParam Bundle theInput
    , RequestDetails theRequestDetails) {
        ourLog.info("received bundle {}", theInput.getId());
        ourLog.info("request detail : {}", theRequestDetails.getCompleteUrl());

        ArrayList<IBaseResource> resList = new ArrayList<IBaseResource>();

        try {
            for (BundleEntryComponent ent : theInput.getEntry()) {
                Resource res = ent.getResource();
                ResourceType resType = res.getResourceType();
                BundleEntryRequestComponent request = ent.getRequest();
                

                if (ent.getRequest().getMethod() != HTTPVerb.POST) {
                    ourLog.info("skipped not POST bundle entry {} {}", res.getId(), res.getResourceType());
                    continue;
                }

                if (resType == ResourceType.Patient) {
                    ourLog.info("found patient {} in bundle", res.getId());
                    resList.add(res);
                    patientProvider.update((Patient)res, "", null);
                    
                } else if (resType == ResourceType.Observation) {
                    ourLog.info("found obs {} in bundle", res.getId());
                    resList.add(res);
                    observationProvider.update((Observation)res, "", null);
                }
            }
            this.getAllResources().addAll(resList);
            
            Bundle retVal = new Bundle();
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue().setSeverity(IssueSeverity.INFORMATION)
                .setCode(IssueType.INFORMATIONAL).setDiagnostics("tx success");
            
            retVal.addEntry()
                .setResource(outcome)
                .getResponse()
                .setStatus("200");
            return retVal;
        } catch (Exception e) {
            Bundle retVal = new Bundle();
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue().setSeverity(IssueSeverity.ERROR)
                .setCode(IssueType.EXCEPTION).setDiagnostics(e.toString());
            
            retVal.addEntry()
                .setResource(outcome)
                .getResponse()
                .setStatus("500");
            return retVal;
        }
    }

    
}
