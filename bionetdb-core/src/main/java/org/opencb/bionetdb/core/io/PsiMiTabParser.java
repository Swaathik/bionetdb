package org.opencb.bionetdb.core.io;

import org.neo4j.cypher.internal.compiler.v2_0.ast.In;
import org.opencb.bionetdb.core.models.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import psidev.psi.mi.tab.PsimiTabReader;
import psidev.psi.mi.tab.model.*;

/**
 * Created by dapregi on 8/09/15.
 */
public class PsiMiTabParser {

    private static final String INTACT_FEAT = "intact.";

    public PsiMiTabParser() {
        init();
    }

    private void init() {
    }

    public Network parse(Path path, String species) throws IOException {
        Network network = new Network();

        // Reading GZip input stream
        InputStream inputStream;
        if (path.toFile().getName().endsWith(".gz")) {
            inputStream = new GZIPInputStream(new FileInputStream(path.toFile()));
        } else {
            inputStream = Files.newInputStream(path);
        }

        // Retrieving species of interest
        Set<String> speciesSet = new HashSet<>(Arrays.asList(species.split(",")));

        // Iterate through PSI-MI TAB interactions
        PsimiTabReader psiMiTabReader = new PsimiTabReader();
        Iterator<BinaryInteraction> iterator = psiMiTabReader.iterate(inputStream);
        boolean isSpeciesA = false;
        boolean isSpeciesB = false;
        while (iterator.hasNext()) {
            BinaryInteraction binaryInteraction = iterator.next();

            isSpeciesA = false;
            isSpeciesB = false;

            if (binaryInteraction.getInteractorA() == null || binaryInteraction.getInteractorB() == null) {
                continue;
            }

            // Check if interactors are from the desired species
            Collection<CrossReference> crA = binaryInteraction.getInteractorA().getOrganism().getIdentifiers();
            for (CrossReference crAElement : crA) {
                if (speciesSet.contains(crAElement.getText())) {
                    isSpeciesA = true;
                    break;
                }
            }
            Collection<CrossReference> crB = binaryInteraction.getInteractorB().getOrganism().getIdentifiers();
            for (CrossReference crBElement : crB) {
                if (speciesSet.contains(crBElement.getText())) {
                    isSpeciesB = true;
                    break;
                }
            }

            if (isSpeciesA && isSpeciesB) {

                PhysicalEntity peA = createPhysicalEntity(binaryInteraction.getInteractorA());
                PhysicalEntity peB = createPhysicalEntity(binaryInteraction.getInteractorB());

                Interaction interaction = createInteraction(binaryInteraction);

                // source
                List<CrossReference> cRs = binaryInteraction.getSourceDatabases();
                for (CrossReference cR : cRs) {
                    if (cR.getDatabase() != null) {
                        peA.getSource().add(cR.getDatabase());
                        peB.getSource().add(cR.getDatabase());
                        interaction.getSource().add(cR.getDatabase());
                    }
                }

                network.setPhysicalEntity(peA);
                network.setPhysicalEntity(peB);
                network.setInteraction(interaction);

            }
        }
        return network;
    }

    private PhysicalEntity createPhysicalEntity(Interactor interactor) {
        PhysicalEntity physicalEntity = null;

        String interactorType = "undefined";
        List<CrossReference> crossReferenceListInteractorTypes = interactor.getInteractorTypes();
        for (CrossReference crossReference : crossReferenceListInteractorTypes) {
            interactorType = crossReference.getText();
        }

        switch (interactorType) {
            case "gene":
            case "deoxyribonucleic acid":
            case "single stranded deoxyribonucleic acid":
            case "double stranded deoxyribonucleic acid":
                physicalEntity = createDna(interactor);
                break;
            case "nucleic acid":
                // NOPE
                break;
            case "ribonucleic acid":
            case "messenger rna":
            case "transfer rna":
            case "small nuclear rna":
            case "ribosomal rna":
            case "long non-coding ribonucleic acid":
                physicalEntity = createRna(interactor);
                break;
            case "protein":
            case "peptide":
                physicalEntity = createProtein(interactor);
                break;
            case "complex":
                physicalEntity = createComplex(interactor);
                break;
            case "small molecule":
            case "poly adenine":
                physicalEntity = createSmallMolecule(interactor);
                break;
            case "undefined":
                physicalEntity = createUndefinedEntity(interactor);
                break;

        }
        return physicalEntity;
    }

    private Dna createDna(Interactor interactor) {
        Dna dna = new Dna();

        // Common properties
        setPhysicalEntityCommonProperties(interactor, dna);

        return dna;
    }

    private Rna createRna(Interactor interactor) {
        Rna rna = new Rna();

        String interactorType = "undefined";
        List<CrossReference> crossReferenceListInteractorTypes = interactor.getInteractorTypes();
        for (CrossReference crossReference : crossReferenceListInteractorTypes) {
            interactorType = crossReference.getText();
        }

        switch (interactorType) {
            case "ribonucleic acid":
                break;
            case "messenger rna":
                rna.setRnaType(Rna.RnaType.MRNA);
                break;
            case "transfer rna":
                rna.setRnaType(Rna.RnaType.TRNA);
                break;
            case "small nuclear rna":
                rna.setRnaType(Rna.RnaType.SNRNA);
                break;
            case "ribosomal rna":
                rna.setRnaType(Rna.RnaType.RRNA);
                break;
            case "long non-coding ribonucleic acid":
                rna.setRnaType(Rna.RnaType.LNCRNA);
                break;
        }

        // Common properties
        setPhysicalEntityCommonProperties(interactor, rna);

        return rna;
    }

    private Protein createProtein(Interactor interactor) {
        Protein protein = new Protein();

        // Common properties
        setPhysicalEntityCommonProperties(interactor, protein);

        // isPeptide
        String interactorType = "";
        List<CrossReference> crossReferenceListInteractorTypes = interactor.getInteractorTypes();
        for (CrossReference crossReference : crossReferenceListInteractorTypes) {
            interactorType = crossReference.getText();
        }
        if (interactorType.equals("peptide")) {
            protein.setPeptide(true);
        }

        // Domains
        for (CrossReference cR : interactor.getXrefs()) {
            if (cR.getDatabase() != null) {
                String source = cR.getDatabase();
                if (source.equals("interpro") || source.equals("rcsb pdb")) {
                    Xref xref = new Xref();
                    xref.setSource(cR.getDatabase());
                    xref.setId(cR.getIdentifier());

                    Domain domain = new Domain();
                    if (cR.getText() != null) {
                        domain.setName(cR.getText());
                    }
                    domain.setXref(xref);

                    protein.getDomains().add(domain);
                }
            }
        }

        return protein;
    }

    private Complex createComplex(Interactor interactor) {
        Complex complex = new Complex();

        // Common properties
        setPhysicalEntityCommonProperties(interactor, complex);

        return complex;
    }

    private SmallMolecule createSmallMolecule(Interactor interactor) {
        SmallMolecule smallMolecule = new SmallMolecule();

        // Common properties
        setPhysicalEntityCommonProperties(interactor, smallMolecule);

        return smallMolecule;
    }

    private UndefinedEntity createUndefinedEntity(Interactor interactor) {
        UndefinedEntity undefinedEntity = new UndefinedEntity();

        // Common properties
        setPhysicalEntityCommonProperties(interactor, undefinedEntity);

        return undefinedEntity;
    }

    private void setPhysicalEntityCommonProperties(Interactor interactor, PhysicalEntity physicalEntity) {

        // xrefs
        List<CrossReference> cRList = new ArrayList<>();
        cRList.addAll(interactor.getXrefs());
        cRList.addAll(interactor.getAlternativeIdentifiers());
        for (CrossReference cR : cRList) {
            if (cR.getDatabase() != null) {
                String source = cR.getDatabase().toLowerCase();
                if (source.equals("sbo") || source.equals("go") || source.equals("mi") || source.equals("ec")) {
                    Ontology ontology = new Ontology();
                    ontology.setSource(cR.getDatabase());
                    ontology.setId(cR.getIdentifier());
                    ontology.setName(cR.getText());
                    physicalEntity.setOntology(ontology);
                } else if (source.equals("pubmed")) {
                    Publication publication = new Publication();
                    publication.setSource(cR.getDatabase());
                    publication.setId(cR.getIdentifier());
                    physicalEntity.setPublication(publication);
                } else if (source.equals("interpro") || source.equals("rcsb pdb")) {
                    continue; // This info will be retrieved in createProtein() method
                } else {
                    Xref xref = new Xref();
                    xref.setSource(cR.getDatabase());
                    xref.setId(cR.getIdentifier());
                    physicalEntity.setXref(xref);
                }
            }
        }
        for (Alias alias : interactor.getAliases()) {
            Xref xref = new Xref();
            xref.setSource(alias.getDbSource());
            xref.setId(alias.getName());
            physicalEntity.setXref(xref);
        }
        for (CrossReference id : interactor.getIdentifiers()) {
            Xref xref = new Xref();
            xref.setSource(id.getDatabase());
            xref.setId(id.getIdentifier());
            physicalEntity.setXref(xref);
        }

        // id
        // TODO

        // name
        // TODO

        // comments
        List <String> comments = new ArrayList<>();
        for (Annotation annotation : interactor.getAnnotations()) {
            String comment = annotation.getText();
            if (comment != null && (comment.equals("mint") || comment.equals("homomint") || comment.equals("domino"))) {
                continue;
            }
            comments.add(comment);
        }
        physicalEntity.getAttributes().put(INTACT_FEAT + "comment", comments);

    }

    private Interaction createInteraction(BinaryInteraction binaryInteraction) {
        Interaction interaction = new Interaction();

        String interactionType = "undefined";
        List<CrossReference> crossReferenceListInteractionTypes = binaryInteraction.getInteractionTypes();
        for (CrossReference crossReference : crossReferenceListInteractionTypes) {
            interactionType = crossReference.getText();
        }

        if (Pattern.matches("(?m)^(?=.*reaction|.*cleavage|lipid addition|.*elongation|phosphopantetheinylation)((?!transglutamination reaction).)*$", interactionType)) {
            // http://stackoverflow.com/questions/406230/regular-expression-to-match-line-that-doesnt-contain-a-word
            interaction = createReaction(binaryInteraction);
        } else if (Pattern.matches("(.*association|direct interaction|transglutamination reaction|disulfide bond|covalent binding)", interactionType)) {
            interaction = createAssembly(binaryInteraction);
        } else if (Pattern.matches("colocalization", interactionType)) {
            interaction = createColocalization(binaryInteraction);
        }

        return interaction;
    }


    private Reaction createReaction(BinaryInteraction binaryInteraction) {
        Reaction reaction = new Reaction();

        // Common properties
        setInteractionCommonProperties(binaryInteraction, reaction);

        return reaction;
    }

    private Reaction createAssembly(BinaryInteraction binaryInteraction) {
        Reaction assembly = new Reaction();
        assembly.setReactionType(Reaction.ReactionType.ASSEMBLY);

        // Common properties
        setInteractionCommonProperties(binaryInteraction, assembly);

        return assembly;
    }

    private Interaction createColocalization(BinaryInteraction binaryInteraction) {
        Interaction colocalization = new Interaction();
        colocalization.setType(Interaction.Type.COLOCALIZATION);

        // Common properties
        setInteractionCommonProperties(binaryInteraction, colocalization);

        return colocalization;
    }

    private void setInteractionCommonProperties(BinaryInteraction binaryInteraction, Interaction interaction) {

        // xrefs
        List<CrossReference> cRs = binaryInteraction.getXrefs();
        for (CrossReference cR : cRs) {
            if (cR.getDatabase() != null) {
                String source = cR.getDatabase().toLowerCase();
                if (source.equals("go") || source.equals("psi-mi") || source.equals("omim")) {
                    Ontology ontology = new Ontology();
                    ontology.setSource(cR.getDatabase());
                    ontology.setId(cR.getIdentifier());
                    ontology.setName(cR.getText());
                    interaction.setOntology(ontology);
                } else if (source.equals("pubmed")) {
                    Publication publication = new Publication();
                    publication.setSource(cR.getDatabase());
                    publication.setId(cR.getIdentifier());
                    interaction.setPublication(publication);
                } else {
                    Xref xref = new Xref();
                    xref.setSource(cR.getDatabase());
                    xref.setId(cR.getIdentifier());
                    interaction.setXref(xref);
                }
            }
        }
        List<CrossReference> ids =  binaryInteraction.getInteractionAcs();
        for (CrossReference id : ids) {
            interaction.setId(id.getIdentifier());
            interaction.setName(id.getIdentifier());
        }

        // id
        // TODO

        // name
        // TODO

        // publications
        List<CrossReference> pubs = binaryInteraction.getPublications();
        for (CrossReference pub : pubs) {
            Publication publication = new Publication();
            publication.setSource(pub.getDatabase());
            publication.setId(pub.getIdentifier());
            interaction.setPublication(publication);
        }

        // comments
        List <String> comments = new ArrayList<>();
        List<Annotation> annotations = binaryInteraction.getAnnotations();
        for (Annotation annotation : annotations) {
            String comment = annotation.getText();
            if (comment != null && (comment.equals("mint") || comment.equals("homomint"))) {
                continue;
            }
            comments.add(comment);
        }
        interaction.getAttributes().put(INTACT_FEAT + "comment", comments);


    }
}

/*
     37 Feature(s)_interactor_A
     38 Feature(s)_interactor_B
     39 Stoichiometry(s)_interactor_A  --> interaction
     40 Stoichiometry(s)_interactor_B  --> interaction
*/
/*
Interaction
        protected List<String> participants;

PE
        protected List<String> participantOfInteraction;
        protected List<Map<String, Object>> features;
*/