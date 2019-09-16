/*
 * CascadesRuleCall.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2019 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.foundationdb.record.query.plan.temp;

import com.apple.foundationdb.annotation.API;
import com.apple.foundationdb.record.RecordCoreArgumentException;
import com.apple.foundationdb.record.query.plan.temp.matchers.PlannerBindings;

import javax.annotation.Nonnull;

/**
 * A rule call implementation for the {@link CascadesPlanner}. This rule call implements the logic for handling new
 * expressions as they are generated by a {@link PlannerRule#onMatch(PlannerRuleCall)} and passed to the rule call
 * via the {@link #yield(ExpressionRef)} method, which consists primarily of manipulating the implicit a memo data
 * structure defined by {@link GroupExpressionRef}s and {@link PlannerExpression}s.
 */
@API(API.Status.EXPERIMENTAL)
public class CascadesRuleCall implements PlannerRuleCall {
    @Nonnull
    private final PlannerRule<? extends PlannerExpression> rule;
    @Nonnull
    private final GroupExpressionRef<PlannerExpression> root;
    @Nonnull
    private final PlannerBindings bindings;
    @Nonnull
    private final PlanContext context;
    @Nonnull
    private final PlannerExpressionPointerSet<PlannerExpression> newExpressions;

    public CascadesRuleCall(@Nonnull PlanContext context,
                             @Nonnull PlannerRule<? extends PlannerExpression> rule,
                             @Nonnull GroupExpressionRef<PlannerExpression> root,
                             @Nonnull PlannerBindings bindings) {
        this.context = context;
        this.rule = rule;
        this.root = root;
        this.bindings = bindings;
        this.newExpressions = new PlannerExpressionPointerSet<>();
    }

    public void run() {
        rule.onMatch(this);
    }

    @Override
    @Nonnull
    public PlannerBindings getBindings() {
        return bindings;
    }

    @Override
    @Nonnull
    public PlanContext getContext() {
        return context;
    }

    @Override
    @SuppressWarnings({"unchecked", "PMD.CompareObjectsWithEquals"}) // deliberate use of == equality check for short-circuit condition
    public void yield(@Nonnull ExpressionRef<? extends PlannerExpression> expression) {
        if (expression == root) {
            return;
        }
        if (expression instanceof GroupExpressionRef) {
            GroupExpressionRef<PlannerExpression> groupExpressionRef = (GroupExpressionRef<PlannerExpression>) expression;
            for (PlannerExpression member : groupExpressionRef.getMembers()) {
                if (!root.containsInMemo(member)) {
                    root.insert(member);
                    newExpressions.add(member);
                }
            }
        } else {
            throw new RecordCoreArgumentException("found a non-group reference in an expression used by the Cascades planner");
        }
    }

    @Override
    public <U extends PlannerExpression> ExpressionRef<U> ref(U expression) {
        return GroupExpressionRef.of(expression);
    }

    @Nonnull
    PlannerExpressionPointerSet<PlannerExpression> getNewExpressions() {
        return newExpressions;
    }
}