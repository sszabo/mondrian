/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2014 Pentaho
// All Rights Reserved.
*/
package mondrian.rolap;

import mondrian.calc.ResultStyle;
import mondrian.calc.TupleCursor;
import mondrian.calc.TupleList;
import mondrian.calc.impl.ListTupleList;
import mondrian.mdx.LevelExpr;
import mondrian.mdx.ResolvedFunCall;
import mondrian.olap.Evaluator;
import mondrian.olap.Exp;
import mondrian.olap.FunDef;
import mondrian.olap.Member;
import mondrian.olap.MondrianProperties;
import mondrian.olap.NativeEvaluator;
import mondrian.olap.fun.FunDefBase;
import mondrian.olap.fun.LevelMembersFunDef;
import mondrian.olap.fun.NonEmptyCrossJoinFunDef;
import mondrian.olap.type.MemberType;
import mondrian.olap.type.SetType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * This class houses a group of static methods used by various functions
 * when performing many to many related aggregations.
 *
 * @author Will Gorman (wgorman@pentaho.com)
 *
 */
public class ManyToManyUtil {
    /**
     * This method builds a cartesian product of the provided members.
     *
     * It is marked protected so that it can be tested.
     *
     * @param newMembersList the additional members to add to the tuple list.
     * @param newList the new tuple list to populate
     * @param mlist tuple list containing the initial set of members to be
     *        joined with
     */
    protected static void buildCartesianProduct(
        List<List<List<Member>>> newMembersList,
        TupleList newList,
        Member[] mlist)
    {
        List<List<Member>> totalmembers = new ArrayList<List<Member>>();
        totalmembers.add(new ArrayList<Member>());

        for (int i = 0 ; i < newMembersList.size(); i++) {
            List<List<Member>> newMembers = newMembersList.get(i);
            if (i < newMembersList.size() - 1) {
                // expand on total members with next level deep
                List<List<Member>> ntotalmembers =
                    new ArrayList<List<Member>>();
                for (List<Member> m : newMembers) {
                    for (List<Member> members : totalmembers) {
                        List<Member> newmembers =
                            new ArrayList<Member>(members);
                        newmembers.addAll(m);
                        ntotalmembers.add(newmembers);
                    }
                }
                totalmembers = ntotalmembers;
            } else {
                // last iteration, add tuples
                for (List<Member> m : newMembers) {
                    for (List<Member> tm : totalmembers) {
                        for (int j = 0; j < tm.size(); j++) {
                            mlist[mlist.length - tm.size() - m.size() + j] =
                                tm.get(j);
                        }
                        // populate member array
                        for (int j = 1; j <= m.size(); j++) {
                          mlist[mlist.length - j] = m.get(m.size() - j);
                        }
                        // future improvement - optimize dedupe
                        boolean duplicate = false;
                        for (int j = 0; j < newList.size(); j++) {
                            List<Member> tc = newList.get(j);
                            boolean matches = true;
                            for (int k = 0;
                                k < mlist.length && matches; k++)
                            {
                                if (!mlist[k].equals(tc.get(k))) {
                                    matches = false;
                                }
                            }
                            if (matches) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            newList.addTuple(mlist);
                        }
                    }
                }
            }
        }
    }

    /**
     * this method returns the correct hierarchy for m2m functions for both
     * regular and virtual cubes.
     *
     * @param evaluator the current evaluator to determine the cube
     * @param m the member to resolve the hierarchy for
     * @return the rolap cube hierarchy
     */
    private static RolapCubeHierarchy getBaseCubeHierarchyForMember(
        Evaluator evaluator,
        Member m)
    {
        RolapCube virtualCube = (RolapCube) evaluator.getCube();
        RolapCube baseCube = (RolapCube) evaluator.getMeasureCube();
        RolapCubeHierarchy hier = null;
        if (!virtualCube.isVirtual()) {
            if (m.getHierarchy() instanceof RolapCubeHierarchy) {
                hier = (RolapCubeHierarchy)m.getHierarchy();
            }
        } else {
            // for virtual cubes, we need access to the base cube level
            // this is dependent on the current measure at play.
            if (m.getLevel() instanceof RolapCubeLevel) {
                RolapCubeLevel baseCubeLevel =
                    baseCube.findBaseCubeLevel(
                        (RolapCubeLevel)m.getLevel());
                if (baseCubeLevel != null) {
                    hier = baseCubeLevel.getHierarchy();
                }
            }
        }
        return hier;
    }

    /**
     * This method retrieves a list of bridge members related to a m2m member.
     *
     * @param evaluator
     * @param m
     * @param parentHierarchyList
     * @return
     */
    private static TupleList getBridgeMembers(
        Evaluator evaluator,
        RolapCubeMember m,
        List<RolapCubeHierarchy> bridgeHierarchyList)
    {
        TupleList newList;
        Evaluator neweval = evaluator.push();
        // clear out the slicer
        List<Member> slicer = ((RolapEvaluator)neweval).getSlicerMembers();
        for (Member sm : slicer) {
            Member dm = sm.getHierarchy().getDefaultMember();
            neweval.setContext(dm);
        }
        slicer.clear();
        neweval.setContext(m);

        // force native non-empty to on for this calculation.
        // an alternative approach would be to throw an
        // exception if native non empty is not enabled.

        boolean nativeNonEmpty =
            MondrianProperties.instance().EnableNativeNonEmpty.get();
        if (nativeNonEmpty == false) {
            MondrianProperties.instance().EnableNativeNonEmpty.set(true);
        }
        RolapCubeHierarchy h = bridgeHierarchyList.get(0);
        if (bridgeHierarchyList.size() == 1) {
            newList = new ListTupleList(1, new ArrayList<Member>());
            // we use the last level in the "bridge" hierarchy
            List<Member> members = h.getRolapSchema()
                .getSchemaReader().getLevelMembers(
                    h.getLevels()[h.getLevels().length - 1],
                    neweval);
            for (Member mem : members) {
                newList.add(Collections.singletonList(mem));
            }
        } else {
            // if there is more than one bridge, we need to get a tuple back of
            // the related bridge members.
            // Create an internal call to the native evaluator for a crossjoin
            // of the related bridge members for this specific m2m member.
            // nest the crossjoins if more than 2 hierarchies are involved.

            FunDef nonEmptyCrossJoin =
                new NonEmptyCrossJoinFunDef(
                    new FunDefBase("NonEmptyCrossJoin", null, "fxxx") {}
                );
            Exp exp[] = new Exp[2];
            for (int j = 0; j < 2; j++) {
                RolapCubeHierarchy h2 = bridgeHierarchyList.get(j);
                exp[j] =
                    new ResolvedFunCall(
                        LevelMembersFunDef.INSTANCE,
                        new Exp[] {
                            new LevelExpr(
                                h2.getLevels()[h2.getLevels().length - 1])},
                        new SetType(MemberType.Unknown));
            }
            // add crossjoins for the remaining levels.
            for (int j = 2; j < bridgeHierarchyList.size(); j++ ) {
                RolapCubeHierarchy h2 = bridgeHierarchyList.get(j);
                Exp exp2[] = new Exp[2];
                exp2[0] = new ResolvedFunCall(
                    nonEmptyCrossJoin,
                    exp,
                    new SetType(MemberType.Unknown));
                exp2[1] = new ResolvedFunCall(
                    LevelMembersFunDef.INSTANCE,
                    new Exp[] {
                        new LevelExpr(
                            h2.getLevels()[h2.getLevels().length - 1])},
                    new SetType(MemberType.Unknown));
                exp = exp2;
            }

            neweval.setNonEmpty(true);
            NativeEvaluator nativeEvaluator =
                h.getRolapSchema()
                .getSchemaReader().getNativeSetEvaluator(
                    nonEmptyCrossJoin, exp, neweval, null);
            newList = (TupleList) nativeEvaluator.execute(ResultStyle.LIST);
        }
        if (nativeNonEmpty == false) {
            MondrianProperties.instance().EnableNativeNonEmpty
                .set(false);
        }
        return newList;
    }

    /**
     * This function converts many to many members to their corresponding
     * bridge members in many to many dimensions, allowing for the
     * aggregation to calculate correct results in the context of those
     * bridge members vs. the many to many members.
     *
     * There are optimizations that still could be implemented:
     *  - check to see if a single many to many member is in play, if so,
     *    no need to replace it with it's bridge
     *  - we could execute a single custom query to determine the related
     *    bridge members for all many to many members of a specific
     *    hierarchy. At the moment we issue separate queries for each
     *    member in play.
     *  - we could improve the performance of deduplication of tuples
     *
     * @param evaluator the current evaluator
     * @param tupleList the original tuple list
     * @return a new tuple list
     */
    public static TupleList processManyToManyMembers(
        Evaluator evaluator,
        TupleList tupleList)
    {
        // first determine if many to many members are present in the tuple
        // list, we evaluate the first tuple.
        boolean m2m = false;
        List<Integer> m2mIndexes = new ArrayList<Integer>();
        int totalLevels = 0;
        List<List<RolapCubeHierarchy>> m2mHierarchies =
            new ArrayList<List<RolapCubeHierarchy>>();
        if (tupleList.size() == 0) {
            return tupleList;
        }

        List<Member> firstTuple = tupleList.get(0);
        // for each member of the tuple, determine if it's in a many to
        // many hierarchy and if so add it to the index for later
        // processing
        for (int i = 0; i < firstTuple.size(); i++) {
            Member m = firstTuple.get(i);
            RolapCubeHierarchy hier =
                getBaseCubeHierarchyForMember(evaluator, m);
            if (hier != null && hier.getManyToManyHierarchies() != null) {
                m2mIndexes.add(i);
                m2mHierarchies.add(hier.getManyToManyHierarchies());
                totalLevels += hier.getManyToManyHierarchies().size();
                m2m = true;
            }
        }

        // note that another check that we can perform is if this m2m is
        // using only 1 member from each m2m dim, if so, no need to use
        // alternative join paths
        if (!m2m) {
            return tupleList;
        }

        TupleList newList =
            new ListTupleList(
                tupleList.getArity() + totalLevels,
                new ArrayList<Member>());
        final TupleCursor cursor = tupleList.tupleCursor();
        Member mlist[] = new Member[cursor.getArity() + totalLevels];

        // for each tuple, lookup the list of members for each m2m member,
        // and then perform a cartesian product on those m2m values
        // resulting in new tuples
        while (cursor.forward()) {
            // future improvement - we could gather all the members from a
            // m2m dim and then issue a single SQL query to get their
            // related m2m hierarchy members, then traverse the list again
            // populating the m2m hierarchy
            List<List<List<Member>>> newMembersList =
                new ArrayList<List<List<Member>>>();
            for (int i = 0; i < cursor.getArity(); i++) {
                if (m2mIndexes.contains(i)) {
                    int m2mIndex = m2mIndexes.indexOf(i);
                    List<List<Member>> newMembers =
                        new ArrayList<List<Member>>();
                    newMembersList.add(newMembers);
                    RolapCubeMember m = (RolapCubeMember)cursor.member(i);
                    newMembers.addAll(
                        getBridgeMembers(
                            evaluator, m, m2mHierarchies.get(m2mIndex)));
                    // change the scope of the m2m dim to be the all member
                    mlist[i] = m.getHierarchy().getAllMember();
                } else {
                  // non m2m member
                  mlist[i] = cursor.member(i);
                }
            }
            // build cartesian product of new members list
            buildCartesianProduct(newMembersList, newList, mlist);
        } // for each tuple
        return newList;
    }

    /**
     * swap out many to many members with their bridge members.
     * note that the slicer experience today is not tuple
     * aware, which can cause issues in general (see MONDRIAN-1861)
     *
     * This is used by the Native Filter and Native Top Count implementations.
     *
     * @param original evaluator
     * @return new evaluator
     */
    public static RolapEvaluator getManyToManyEvaluator(
        RolapEvaluator evaluator)
    {
        RolapEvaluator neweval = null;
        List<Member> slicer = ((RolapEvaluator)evaluator).getSlicerMembers();
        List<Member> newSlicer = new ArrayList<Member>();
        boolean manyToMany = false;
        for (Member m : slicer) {
            RolapCubeHierarchy hier =
                getBaseCubeHierarchyForMember(evaluator, m);
            if (!(m instanceof RolapCubeMember)
                || hier.getManyToManyHierarchies() == null)
            {
                newSlicer.add(m);
            } else {
                if (neweval == null) {
                    neweval = (RolapEvaluator)evaluator.push();
                }
                manyToMany = true;
                Member dm = hier.getDefaultMember();
                neweval.setContext(dm);
                TupleList list =
                    getBridgeMembers(
                        evaluator,
                        (RolapCubeMember)m,
                        hier.getManyToManyHierarchies());
                for (List<Member> nmlist : list) {
                    for (Member nm : nmlist) {
                        if (!newSlicer.contains(nm)) {
                            newSlicer.add(nm);
                        }
                    }
                }
            }
        }
        if (!manyToMany) {
          return evaluator;
        }
        neweval.getSlicerMembers().clear();
        for (Member m : newSlicer) {
          neweval.setSlicerContext(m);
        }
        return neweval;
    }
}
// End ManyToManyUtil.java