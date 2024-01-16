#!/bin/bash

mvn exec:java -Dexec.mainClass="ca.uhn.fhir.letsbuild.upload.CsvDataUploader"
