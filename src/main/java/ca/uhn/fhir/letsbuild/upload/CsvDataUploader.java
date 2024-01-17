package ca.uhn.fhir.letsbuild.upload;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;

import com.google.common.base.Charsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvDataUploader {

    private static final Logger ourLog = LoggerFactory.getLogger(CsvDataUploader.class);

    public static void main(String[] theArgs) throws Exception {

        FhirContext ctx = FhirContext.forR4Cached();

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.setId("bundle_" + DateTimeType.now().toString());

        ArrayList<Resource> resList = new ArrayList<Resource>();

        // Open the CSV file for reading
        try (InputStream inputStream = new FileInputStream("src/main/resources/sample-data.csv")) {
            Reader reader = new InputStreamReader(inputStream, Charsets.UTF_8);

            CSVFormat format = CSVFormat.EXCEL
                    .withFirstRecordAsHeader()
                    .withDelimiter(',');
            CSVParser csvParser = format.parse(reader);

            // Loop throw each row in the CSV file
            for (CSVRecord nextRecord : csvParser.getRecords()) {

                // Sequence number - This could be used as an ID for generated resources
                String seqN = nextRecord.get("SEQN");

                // Timestamp - This will be formatted in ISO8601 format
                String timestamp = nextRecord.get("TIMESTAMP");

                // Patient ID
                String patientId = nextRecord.get("PATIENT_ID");

                // Add a log line - you can copy this to add more helpful logging
                //ourLog.info("Processing row with sequence {} for patient ID {}", seqN, patientId);

                // Patient Family Name
                String patientFamilyName = nextRecord.get("PATIENT_FAMILYNAME");

                // Patient Given Name
                String patientGivenName = nextRecord.get("PATIENT_GIVENNAME");

                // Patient Gender - Values will be "M" or "F"
                String patientGender = nextRecord.get("PATIENT_GENDER");

                // White blood cell count - This corresponds to LOINC code:
                // Code:        6690-2
                // Display:     Leukocytes [#/volume] in Blood by Automated count
                // Unit System: http://unitsofmeasure.org
                // Unit Code:   10*3/uL
                String rbc = nextRecord.get("RBC");

                // White blood cell count - This corresponds to LOINC code:
                // Code:        789-8
                // Display:     Erythrocytes [#/volume] in Blood by Automated count
                // Unit System: http://unitsofmeasure.org
                // Unit Code:   10*6/uL
                String wbc = nextRecord.get("WBC");

                // Hemoglobin
                // Code:        718-7
                // Display:     Hemoglobin [Mass/volume] in Blood
                // Unit System: http://unitsofmeasure.org
                // Unit Code:   g/dL
                String hb = nextRecord.get("HB");

                // Day 1 Exercise:
                // Create a Patient resource, and 3 Observation resources, and
                // log them to the console.
                Patient patient = new Patient();

                // patient resource id
                patient.setId(patientId);
                
                patient.addIdentifier().setSystem("http://acme.org/mrns")
                    .setValue(patientId);

                // patient name
                patient.addName()
                    .setFamily(patientFamilyName)
                    .addGiven(patientGivenName);
                    
                // name.setGiven(new ArrayList<StringType>(
                //     Arrays.asList(new StringType[] {new StringType(patientGivenName)})));

                // List<StringType> givenNames = new ArrayList<StringType>();
                // givenNames.add(new StringType(patientGivenName));
                // name.setGiven(givenNames);

                // patient gender
                switch (patientGender) {
                    case "F":
                        patient.setGender(AdministrativeGender.FEMALE);
                        break;
                    case "M":
                        patient.setGender(AdministrativeGender.MALE);
                    default:
                        patient.setGender(AdministrativeGender.UNKNOWN);
                }

                //.setCode("789-8").setDisplay("Erythrocytes [#/volume] in Blood by Automated count");
        
                Observation obs_rbc = CreateObservation(patient, seqN, timestamp, "789-8"
                , "Erythrocytes [#/volume] in Blood by Automated count", rbc
                , "10*6/uL", "http://unitsofmeasure.org");


                Observation obs_wbc = CreateObservation(patient, seqN, timestamp, "6690-2"
                , "Leukocytes [#/volume] in Blood by Automated count", wbc
                , "10*3/uL", "http://unitsofmeasure.org");

                Observation obs_hb = CreateObservation(patient, seqN, timestamp, "718-7"
                , "Hemoglobin [Mass/volume] in Blood", hb
                , "g/dL", "http://unitsofmeasure.org");
                
                

                // IParser parser = ctx.newJsonParser();
                // parser.setPrettyPrint(true);

                // String patientJsonString = parser.encodeResourceToString(patient);

                // System.out.printf("\n>> created patient resource: id=%s\n", patient.getId().toString());
                // ourLog.info("created patient resource: id={}", patient.getId());
                
                // System.out.println(patientJsonString);

                bundle.addEntry()
                    .setFullUrl(patient.getIdElement().getValue())
                    .setResource(patient)
                    .getRequest()
                    .setUrl("Patient")
                    .setIfNoneExist(hb)
                    .setMethod(Bundle.HTTPVerb.POST);
                
                Observation[] obsList = new Observation[] {
                    obs_rbc, obs_wbc, obs_hb
                };

                for (Observation obs : obsList) {
                    bundle.addEntry()
                        .setResource(obs)
                        .getRequest()
                        .setUrl("Observation")
                        .setMethod(Bundle.HTTPVerb.POST);
                }

                resList.add(patient);
                resList.add(obs_rbc);
                resList.add(obs_wbc);
                resList.add(obs_hb);
            }

        }  // files streams

        String bundleJson = ctx.newJsonParser().setPrettyPrint(true)
            .encodeResourceToString(bundle);
        
        // ourLog.info(bundleJson);

        IGenericClient client = ctx.newRestfulGenericClient("http://localhost:8000");
        client.registerInterceptor(new LoggingInterceptor());

        ourLog.info("capabilities {}", client.capabilities().toString());

        for (Resource r : resList) {
            ourLog.info("sending {} resource: id={}"
                , r.getClass().getSimpleName(), r.getId());

            client.update().resource(r).execute();
        }

        // for (IBaseResource r : resList) {
        //     Patient p;
        //     try { p = (Patient)r; } catch (Exception e) { continue; }
        //     ourLog.info("sending {} resource: id={}"
        //         , p.getClass().getSimpleName(), p.getId());

        //     client.update().resource(p).execute();
        // }

        // List<IBaseResource> resps = client.transaction().withResources(resList).execute();
        // for (IBaseResource item : resps) {
        //     ourLog.info(">>> {}", item);
        // }

        // Bundle response = client.transaction().withBundle(bundle).execute();
        // ourLog.info(">>> {}", response);

    } // main

    public static Observation CreateObservation(Patient patient,
        String seqN, String timestamp, String lonicCode , String lonicDesc
        , String obsValue, String uom, String uomOrg)
    {
        Observation obs = new Observation();
        obs.setId(seqN);
        
        obs.setSubject(new Reference(patient.getIdElement()));

        obs.setStatus(ObservationStatus.FINAL);

        obs.setEffective(new DateTimeType(timestamp));

        obs.getCode().addCoding().setSystem("https://lonic.org")
            .setCode(lonicCode).setDisplay(lonicDesc);

        // obs.setValue(new Quantity()
        //     .setValue(0)
        //     .setUnit("10 trillion/L")
        //     .setSystem("http://unitsofmeasure.org")
        //     .setCode("10*12/L"));

        obs.setValue(new StringType(obsValue));

        return obs;
    }

}
