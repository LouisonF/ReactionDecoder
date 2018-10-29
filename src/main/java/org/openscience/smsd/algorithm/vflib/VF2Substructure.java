/* Copyright (C) 2009-2018  Syed Asad Rahman <asad at ebi.ac.uk>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 * All we ask is that proper credit is given for our work, which includes
 * - but is not limited to - adding the above copyright notice to the beginning
 * of your source code files, and to any copyright notice that you may distribute
 * with programs based on this work.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received commonAtomList copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.smsd.algorithm.vflib;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.isomorphism.AtomMatcher;
import org.openscience.cdk.isomorphism.BondMatcher;
import org.openscience.cdk.isomorphism.Mappings;
import org.openscience.cdk.isomorphism.VentoFoggia;
import org.openscience.cdk.isomorphism.matchers.IQueryAtomContainer;
import org.openscience.cdk.tools.ILoggingTool;
import static org.openscience.cdk.tools.LoggingToolFactory.createLoggingTool;
import org.openscience.smsd.AtomAtomMapping;
import org.openscience.smsd.algorithm.mcgregor.McGregor;
import org.openscience.smsd.algorithm.vflib.vf2.sub.Pattern;
import org.openscience.smsd.algorithm.vflib.vf2.sub.VF;
import org.openscience.smsd.interfaces.IResults;

/**
 * This class should be used to find MCS between source graph and target graph.
 *
 * First the algorithm runs VF lib
 * {@link org.openscience.smsd.algorithm.vflib.VF2MCS} and reports MCS between
 * run source and target graphs. Then these solutions are extended using
 * McGregor {@link org.openscience.smsd.algorithm.mcgregor.McGregor} algorithm
 * where ever required.
 *
 *
 *
 *
 * @author Syed Asad Rahman <asad at ebi.ac.uk>
 */
public class VF2Substructure implements IResults {

    private final boolean DEBUG = false;
    private final List<AtomAtomMapping> allAtomMCS;
    private final List<AtomAtomMapping> allAtomMCSCopy;
    private final List<Map<Integer, Integer>> allMCS;
    private final List<Map<Integer, Integer>> allMCSCopy;
    private List<Map<IAtom, IAtom>> vfLibSolutions;
    private final IAtomContainer source;
    private final IAtomContainer target;
    private final boolean shouldMatchRings;
    private final boolean matchBonds;
    private boolean matchAtomType;
    private int bestHitSize = -1;
    private int countR = 0;
    private int countP = 0;
    private boolean isSubgraph = false;
    private final static ILoggingTool LOGGER
            = createLoggingTool(VF2Substructure.class);

    /**
     * Constructor for an extended VF Algorithm for the MCS search
     *
     * @param source
     * @param target
     * @param shouldMatchBonds
     * @param shouldMatchRings
     * @param matchAtomType
     * @param findallMatches Find all SubGraphs
     */
    public VF2Substructure(IAtomContainer source, IAtomContainer target,
            boolean shouldMatchBonds, boolean shouldMatchRings, boolean matchAtomType, boolean findallMatches) {
        this.source = source;
        this.target = target;
        allAtomMCS = new ArrayList<>();
        allAtomMCSCopy = new ArrayList<>();
        allMCS = new ArrayList<>();
        allMCSCopy = new ArrayList<>();
        this.shouldMatchRings = shouldMatchRings;
        this.matchBonds = shouldMatchBonds;
        this.matchAtomType = matchAtomType;
        if (findallMatches) {
            this.isSubgraph = findSubgraphs();
        } else {
            this.isSubgraph = findSubgraph();
        }
    }

    /**
     * Constructor for an extended VF Algorithm for the MCS search
     *
     * @param source
     * @param target
     * @param findallMatches find all subgraphs
     */
    public VF2Substructure(IQueryAtomContainer source, IAtomContainer target, boolean findallMatches) {
        this.source = source;
        this.target = target;
        allAtomMCS = new ArrayList<>();
        allAtomMCSCopy = new ArrayList<>();
        allMCS = new ArrayList<>();
        allMCSCopy = new ArrayList<>();
        this.shouldMatchRings = true;
        this.matchBonds = true;
        if (findallMatches) {
            this.isSubgraph = findSubgraphs();
        } else {
            this.isSubgraph = findSubgraph();
        }
    }

    /**
     * {@inheritDoc}
     *
     */
    private boolean findSubgraph() {

        if (DEBUG) {
            System.out.println("=======findSubgraph=======");
        }

        if (DEBUG) {
            System.out.println("Calling searchVFCDKMapping");
        }
        boolean timoutVF = searchVFCDKMapping();
        if (!allAtomMCSCopy.isEmpty()
                && allAtomMCSCopy.iterator().next().getCount() == source.getAtomCount()) {
            allAtomMCS.addAll(allAtomMCSCopy);
            allMCS.addAll(allMCSCopy);
        }
        return !allAtomMCS.isEmpty()
                && allAtomMCS.iterator().next().getCount()
                == source.getAtomCount();
    }

    /**
     * {@inheritDoc}
     *
     */
    private boolean findSubgraphs() {

        if (DEBUG) {
            System.out.println("=======findSubgraphs=======");
        }
        boolean flagSubGraph = false;

        AtomMatcher am = AtomMatcher.forElement();
        BondMatcher bm;
        if (this.matchBonds) {
            bm = BondMatcher.forOrder();
        } else {
            bm = BondMatcher.forAny();
        }

        if (source.getAtomCount() <= target.getAtomCount()) {
            org.openscience.cdk.isomorphism.Pattern pattern = VentoFoggia.findSubstructure(source, am, bm); // create pattern
            Mappings limit = pattern.matchAll(target).limit(1);
            flagSubGraph = limit.count() > 0;
            if (DEBUG) {
                System.out.println("t>s limit.count() " + limit.count());
            }
        }

        if (!flagSubGraph) {
            return false;
        }

        if (DEBUG) {
            System.out.println("Calling searchVFMappings");
        }
        //boolean timoutVF = searchVFMappings();
        boolean timoutVF = searchVFCDKMappings();

        boolean flag = isExtensionFeasible();
        if (DEBUG) {
            System.out.println("isExtensionFeasible subgraph " + flag);
        }

        if (flag && !vfLibSolutions.isEmpty()
                && !timoutVF && (!(source instanceof IQueryAtomContainer))) {
            try {
                searchMcGregorMapping();
            } catch (CDKException | IOException ex) {
                LOGGER.error(Level.SEVERE, null, ex);
            }
        } else if (!allAtomMCSCopy.isEmpty()
                && allAtomMCSCopy.iterator().next().getCount() == source.getAtomCount()) {
            allAtomMCS.addAll(allAtomMCSCopy);
            allMCS.addAll(allMCSCopy);
        }
        return !allAtomMCS.isEmpty()
                && allAtomMCS.iterator().next().getCount()
                == source.getAtomCount();
    }

    private synchronized boolean isExtensionFeasible() {
        int commonAtomCount = checkCommonAtomCount(getReactantMol(), getProductMol());
        return commonAtomCount > bestHitSize;
    }

    private boolean hasMap(Map<Integer, Integer> maps, List<Map<Integer, Integer>> mapGlobal) {
        return mapGlobal.stream().anyMatch((test) -> (test.equals(maps)));
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public synchronized List<AtomAtomMapping> getAllAtomMapping() {
        return Collections.unmodifiableList(allAtomMCS);
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public synchronized AtomAtomMapping getFirstAtomMapping() {
        if (allAtomMCS.iterator().hasNext()) {
            return allAtomMCS.iterator().next();
        }
        return new AtomAtomMapping(source, target);
    }

    private synchronized int checkCommonAtomCount(IAtomContainer reactantMolecule, IAtomContainer productMolecule) {
        ArrayList<String> atoms = new ArrayList<>();
        for (int i = 0; i < reactantMolecule.getAtomCount(); i++) {
            atoms.add(reactantMolecule.getAtom(i).getSymbol());
        }
        int common = 0;
        for (int i = 0; i < productMolecule.getAtomCount(); i++) {
            String symbol = productMolecule.getAtom(i).getSymbol();
            if (atoms.contains(symbol)) {
                atoms.remove(symbol);
                common++;
            }
        }
        return common;
    }

    /*
     * Note: VF will search for core hits. Mcgregor will extend the cliques depending of the bond type (sensitive and
     * insensitive).
     */
    private synchronized boolean searchVFMappings() {
        if (DEBUG) {
            System.out.println("searchVFMappings ");
        }
        VF mapper = null;
        if (!(source instanceof IQueryAtomContainer) && !(target instanceof IQueryAtomContainer)) {
            countR = getReactantMol().getAtomCount();
            countP = getProductMol().getAtomCount();
        }

        vfLibSolutions = new ArrayList<>();
        if (source instanceof IQueryAtomContainer) {
            Pattern findSubstructure = VF.findSubstructure((IQueryAtomContainer) source);
            List<Map<IAtom, IAtom>> maps = findSubstructure.matchAll(getProductMol());
            if (maps != null) {
                vfLibSolutions.addAll(maps);
            }
            setVFMappings(true);
        } else if (countR <= countP) {
            Pattern findSubstructure = VF.findSubstructure(this.source, this.matchBonds, this.shouldMatchRings, this.matchAtomType);
            List<Map<IAtom, IAtom>> maps = findSubstructure.matchAll(getProductMol());
            if (maps != null) {
                vfLibSolutions.addAll(maps);
            }
            setVFMappings(true);
        }
        if (DEBUG) {
            System.out.println("Sol count " + vfLibSolutions.size());
            System.out.println("Sol size " + (vfLibSolutions.iterator().hasNext() ? vfLibSolutions.iterator().next().size() : 0));
            System.out.println("MCSSize " + bestHitSize);
            System.out.println("After Sol count " + allMCSCopy.size());
        }
        return mapper != null;
    }

    /*
     * Note: CDK VF will search for core hits. Mcgregor will extend the cliques depending of the bond type (sensitive and
     * insensitive).
     */
    private synchronized boolean searchVFCDKMapping() {
        if (DEBUG) {
            System.out.println("searchVFCDKMappings ");
        }
        VF mapper = null;
        AtomMatcher am;
        BondMatcher bm;

        if (!(source instanceof IQueryAtomContainer)
                && !(target instanceof IQueryAtomContainer)) {

            countR = getReactantMol().getAtomCount();
            countP = getProductMol().getAtomCount();

            am = AtomMatcher.forElement();
            if (matchAtomType) {
                am = AtomMatcher.forElement();
            }

            if (matchBonds) {
                bm = BondMatcher.forOrder();
            } else {
                bm = BondMatcher.forAny();
            }

            if (this.shouldMatchRings) {
                bm = BondMatcher.forStrictOrder();
            }
        } else {
            if (source instanceof IQueryAtomContainer) {
                am = AtomMatcher.forQuery();
            } else {
                am = AtomMatcher.forElement();
            }

            if (source instanceof IQueryAtomContainer) {
                bm = BondMatcher.forQuery();
            } else {
                bm = BondMatcher.forAny();
            }
        }

        vfLibSolutions = new ArrayList<>();
        if (source instanceof IQueryAtomContainer) {
            org.openscience.cdk.isomorphism.Pattern patternVF = VentoFoggia.findSubstructure(source, am, bm); // create pattern
            Mappings matchAll = patternVF.matchAll((IQueryAtomContainer) target).limit(1);
            Iterable<Map<IAtom, IAtom>> toAtomMap = matchAll.toAtomMap();
            for (Map<IAtom, IAtom> map : toAtomMap) {
                vfLibSolutions.add(map);
            }
            setVFMappings(true);
        } else if (countR <= countP) {

            org.openscience.cdk.isomorphism.Pattern patternVF = VentoFoggia.findSubstructure(source, am, bm); // create pattern
            Mappings matchAll = patternVF.matchAll(target).limit(1);
            Iterable<Map<IAtom, IAtom>> toAtomMap = matchAll.toAtomMap();
            for (Map<IAtom, IAtom> map : toAtomMap) {
                vfLibSolutions.add(map);
            }
            setVFMappings(true);
        }

        if (DEBUG) {
            System.out.println("Sol count " + vfLibSolutions.size());
            System.out.println("Sol size " + (vfLibSolutions.iterator().hasNext() ? vfLibSolutions.iterator().next().size() : 0));
            System.out.println("MCSSize " + bestHitSize);
            System.out.println("After Sol count " + allMCSCopy.size());
        }
        return mapper != null;
    }

    /*
     * Note: CDK VF will search for core hits. Mcgregor will extend the cliques depending of the bond type (sensitive and
     * insensitive).
     */
    private synchronized boolean searchVFCDKMappings() {
        if (DEBUG) {
            System.out.println("searchVFCDKMappings ");
        }
        VF mapper = null;
        AtomMatcher am;
        BondMatcher bm;

        if (!(source instanceof IQueryAtomContainer)
                && !(target instanceof IQueryAtomContainer)) {

            countR = getReactantMol().getAtomCount();
            countP = getProductMol().getAtomCount();

            am = AtomMatcher.forElement();
            if (matchAtomType) {
                am = AtomMatcher.forElement();
            }

            if (matchBonds) {
                bm = BondMatcher.forOrder();
            } else {
                bm = BondMatcher.forAny();
            }

            if (this.shouldMatchRings) {
                bm = BondMatcher.forStrictOrder();
            }
        } else {
            if (source instanceof IQueryAtomContainer) {
                am = AtomMatcher.forQuery();
            } else {
                am = AtomMatcher.forElement();
            }

            if (source instanceof IQueryAtomContainer) {
                bm = BondMatcher.forQuery();
            } else {
                bm = BondMatcher.forAny();
            }
        }

        vfLibSolutions = new ArrayList<>();
        if (source instanceof IQueryAtomContainer) {
            org.openscience.cdk.isomorphism.Pattern patternVF = VentoFoggia.findSubstructure(source, am, bm); // create pattern
            Mappings matchAll = patternVF.matchAll((IQueryAtomContainer) target);
            Iterable<Map<IAtom, IAtom>> toAtomMap = matchAll.toAtomMap();
            for (Map<IAtom, IAtom> map : toAtomMap) {
                vfLibSolutions.add(map);
            }
            setVFMappings(true);
        } else if (countR <= countP) {

            org.openscience.cdk.isomorphism.Pattern patternVF = VentoFoggia.findSubstructure(source, am, bm); // create pattern
            Mappings matchAll = patternVF.matchAll(target);
            Iterable<Map<IAtom, IAtom>> toAtomMap = matchAll.toAtomMap();
            for (Map<IAtom, IAtom> map : toAtomMap) {
                vfLibSolutions.add(map);
            }
            setVFMappings(true);
        }

        if (DEBUG) {
            System.out.println("Sol count " + vfLibSolutions.size());
            System.out.println("Sol size " + (vfLibSolutions.iterator().hasNext() ? vfLibSolutions.iterator().next().size() : 0));
            System.out.println("MCSSize " + bestHitSize);
            System.out.println("After Sol count " + allMCSCopy.size());
        }
        return mapper != null;
    }

    private synchronized void searchMcGregorMapping() throws CDKException, IOException {
        List<List<Integer>> mappings = new ArrayList<>();
        boolean ROPFlag = true;
        for (Map<Integer, Integer> firstPassMappings : allMCSCopy) {
            Map<Integer, Integer> extendMapping = new TreeMap<>(firstPassMappings);
            McGregor mgit;
            if (source instanceof IQueryAtomContainer) {
                mgit = new McGregor((IQueryAtomContainer) source, target, mappings, this.matchBonds, this.shouldMatchRings, this.matchAtomType);
                //Start McGregor search
                mgit.startMcGregorIteration((IQueryAtomContainer) source, mgit.getMCSSize(), extendMapping);
            } else {
                extendMapping.clear();
                mgit = new McGregor(target, source, mappings, this.matchBonds, this.shouldMatchRings, this.matchAtomType);
                ROPFlag = false;
                firstPassMappings.entrySet().stream().forEach((map) -> {
                    extendMapping.put(map.getValue(), map.getKey());
                });
                //Start McGregor search
                mgit.startMcGregorIteration(target, mgit.getMCSSize(), extendMapping);
            }
            mappings = mgit.getMappings();
        }
//        System.out.println("\nSol count after MG" + mappings.size());
        setMcGregorMappings(ROPFlag, mappings);
//        System.out.println("After set Sol count MG" + allMCS.size());
//        System.out.println("MCSSize " + bestHitSize + "\n");
    }

    private synchronized void setVFMappings(boolean RONP) {
        int counter = 0;
        for (Map<IAtom, IAtom> solution : vfLibSolutions) {
            AtomAtomMapping atomatomMapping = new AtomAtomMapping(source, target);
            Map<Integer, Integer> indexindexMapping = new TreeMap<>();

            solution.entrySet().stream().forEach((mapping) -> {
                IAtom qAtom;
                IAtom tAtom;
                Integer qIndex;
                Integer tIndex;

                if (RONP) {
                    qAtom = mapping.getKey();
                    tAtom = mapping.getValue();
                    qIndex = source.indexOf(qAtom);
                    tIndex = target.indexOf(tAtom);
                } else {
                    tAtom = mapping.getKey();
                    qAtom = mapping.getValue();
                    qIndex = source.indexOf(qAtom);
                    tIndex = target.indexOf(tAtom);
                }

                if (qIndex != -1 && tIndex != -1) {
                    atomatomMapping.put(qAtom, tAtom);
                    indexindexMapping.put(qIndex, tIndex);
                } else {
                    try {
                        throw new CDKException("Atom index pointing to -1");
                    } catch (CDKException ex) {
                        LOGGER.error(Level.SEVERE, null, ex);
                    }
                }
            });
            if (indexindexMapping.size() > bestHitSize) {
                bestHitSize = indexindexMapping.size();
                allAtomMCSCopy.clear();
                allMCSCopy.clear();
                counter = 0;
            }
            if (!atomatomMapping.isEmpty() && !hasMap(indexindexMapping, allMCSCopy)
                    && indexindexMapping.size() == bestHitSize) {
//                System.out.println("\nvfMCSSize: " + bestHitSize);
                allAtomMCSCopy.add(counter, atomatomMapping);
                allMCSCopy.add(counter, indexindexMapping);
                counter++;
            }
        }
//        System.out.println("After set allMCSCopy " + allMCSCopy);
    }

    private synchronized void setMcGregorMappings(boolean RONP, List<List<Integer>> mappings) throws CDKException {
        int counter = 0;
        for (List<Integer> mapping : mappings) {
            AtomAtomMapping atomatomMapping = new AtomAtomMapping(source, target);
            Map<Integer, Integer> indexindexMapping = new TreeMap<>();
            for (int index = 0; index < mapping.size(); index += 2) {
                IAtom qAtom;
                IAtom tAtom;
                Integer qIndex;
                Integer tIndex;

                if (RONP) {
                    qAtom = getReactantMol().getAtom(mapping.get(index));
                    tAtom = getProductMol().getAtom(mapping.get(index + 1));

                    qIndex = mapping.get(index);
                    tIndex = mapping.get(index + 1);
                } else {
                    qAtom = getReactantMol().getAtom(mapping.get(index + 1));
                    tAtom = getProductMol().getAtom(mapping.get(index));
                    qIndex = mapping.get(index + 1);
                    tIndex = mapping.get(index);
                }

                if (qIndex != null && tIndex != null) {
                    atomatomMapping.put(qAtom, tAtom);
                    indexindexMapping.put(qIndex, tIndex);
                } else {
                    throw new CDKException("Atom index pointing to NULL");
                }
            }

            if (indexindexMapping.size() > bestHitSize) {
                bestHitSize = indexindexMapping.size();
                allAtomMCS.clear();
                allMCS.clear();
                counter = 0;
            }

            if (!atomatomMapping.isEmpty() && !hasMap(indexindexMapping, allMCS)
                    && (indexindexMapping.size()) == bestHitSize) {
                allAtomMCS.add(counter, atomatomMapping);
                allMCS.add(counter, indexindexMapping);
                counter++;
            }
        }
    }

    private synchronized IAtomContainer getReactantMol() {
        return source;
    }

    private synchronized IAtomContainer getProductMol() {
        return target;
    }

    /**
     * @return the isSubgraph
     */
    public boolean isSubgraph() {
        return isSubgraph;
    }
}