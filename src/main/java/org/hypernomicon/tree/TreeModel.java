/*
 * Copyright 2015-2020 Jason Winning
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
 *
 */

package org.hypernomicon.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.hypernomicon.model.records.HDT_Record;
import org.hypernomicon.model.records.RecordType;
import org.hypernomicon.model.relations.RelationSet.RelationType;
import org.hypernomicon.util.BidiOneToManyRecordMap;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.ImmutableSet;

import javafx.scene.control.TreeItem;

import static org.hypernomicon.model.HyperDB.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.model.records.RecordType.*;

public class TreeModel<RowType extends AbstractTreeRow<? extends HDT_Record, RowType>>
{
  final private BidiOneToManyRecordMap parentToChildren;
  final private MappingFromRecordToRows recordToRows;
  final private AbstractTreeWrapper<RowType> treeWrapper;
  final private Set<RecordType> recordTypes = EnumSet.noneOf(RecordType.class);

  private RowType rootRow;
  public boolean pruningOperationInProgress = false;

  public void expandMainBranch()                          { rootRow.treeItem.setExpanded(true); }
  Set<RecordType> getRecordTypes()                        { return Collections.unmodifiableSet(recordTypes); }
  public Set<RowType> getRowsForRecord(HDT_Record record) { return recordToRows.getRowsForRecord(record); }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private class MappingFromRecordToRows
  {
    final private SetMultimap<HDT_Record, RowType> recordToRows = LinkedHashMultimap.create();
    final private TreeCB tcb;

    //---------------------------------------------------------------------------

    private MappingFromRecordToRows(TreeCB tcb)              { this.tcb = tcb; }
    private Set<RowType> getRowsForRecord(HDT_Record record) { return recordToRows.get(record); }
    private void clear()                                     { recordToRows.clear(); }

    //---------------------------------------------------------------------------
    //---------------------------------------------------------------------------

    private void addRow(RowType row)
    {
      HDT_Record record = row.getRecord();

      if ((tcb != null) && (recordToRows.containsKey(record) == false))
        tcb.add(record);

      recordToRows.put(record, row);
    }

    //---------------------------------------------------------------------------
    //---------------------------------------------------------------------------

    private void removeRow(RowType row)
    {
      HDT_Record record = row.getRecord();

      if (recordToRows.remove(record, row) && (tcb != null))
        tcb.checkIfShouldBeRemoved(record);
    }
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public TreeModel(AbstractTreeWrapper<RowType> treeWrapper, TreeCB tcb)
  {
    parentToChildren = new BidiOneToManyRecordMap();
    recordToRows = new MappingFromRecordToRows(tcb);
    this.treeWrapper = treeWrapper;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void clear()
  {
    parentToChildren.clear();
    recordToRows.clear();
    rootRow = null;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void reset(HDT_Record rootRecord)
  {
    clear();

    rootRow = treeWrapper.newRow(rootRecord, this);
    treeWrapper.getRoot().getChildren().add(treeWrapper.getTreeItem(rootRow));
    recordToRows.addRow(rootRow);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  void removeRecord(HDT_Record record)
  {
    ImmutableSet.<HDT_Record>copyOf(parentToChildren.getForwardSet(record)).forEach(child  -> unassignParent(child , record));
    ImmutableSet.<HDT_Record>copyOf(parentToChildren.getReverseSet(record)).forEach(parent -> unassignParent(record, parent));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  void copyTo(TreeModel<RowType> dest)
  {
    parentToChildren.getAllHeads().forEach(parent ->
      parentToChildren.getForwardSet(parent).forEach(child ->
        dest.assignParent(child, parent)));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void unassignParent(HDT_Record child, HDT_Record parent)
  {
    if (parentToChildren.getForwardSet(parent).contains(child) == false) return;

    new ArrayList<>(recordToRows.getRowsForRecord(parent)).forEach(row -> row.treeItem.getChildren().removeIf(childItem ->
    {
      RowType childRow = childItem.getValue();

      if (childRow.getRecord() != child) return false;

      removeChildRows(childRow);
      recordToRows.removeRow(childRow);

      return pruningOperationInProgress == false;  // prevent ConcurrentModificationException
    }));

    parentToChildren.removeForward(parent, child);
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void removeChildRows(RowType parentRow)
  {
    parentRow.treeItem.getChildren().removeIf(childItem ->
    {
      RowType childRow = childItem.getValue();

      removeChildRows(childRow);
      recordToRows.removeRow(childRow);

      return pruningOperationInProgress == false;  // prevent ConcurrentModificationException
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void assignParent(HDT_Record child, HDT_Record parent)
  {
    if (parentToChildren.getForwardSet(parent).contains(child)) return;

    parentToChildren.addForward(parent, child);

    new ArrayList<>(recordToRows.getRowsForRecord(parent)).forEach(row ->
    {
      RowType childRow = treeWrapper.newRow(child, this);

      insertTreeItem(treeWrapper.getTreeItem(row).getChildren(), childRow);
      recordToRows.addRow(childRow);
      addChildRows(childRow);
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void insertTreeItem(List<TreeItem<RowType>> list, RowType newRow)
  {
    addToSortedList(list, treeWrapper.getTreeItem(newRow), sortBasis(TreeItem::getValue));
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void addChildRows(RowType parentRow)
  {
    parentToChildren.getForwardSet(parentRow.getRecord()).forEach(child ->
    {
      RowType childRow = treeWrapper.newRow(child, this);
      recordToRows.addRow(childRow);
      insertTreeItem(treeWrapper.getTreeItem(parentRow).getChildren(), childRow);
      addChildRows(childRow);
    });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public void addParentChildRelation(RelationType relType, boolean forward)
  {
    recordTypes.addAll(EnumSet.of(db.getSubjType(relType), db.getObjType(relType)));

    db.addRelationChangeHandler(relType, forward ?
      (child, parent, affirm) ->
      {
        if (affirm) assignParent(child, parent);
        else        unassignParent(child, parent);
      }
    :
      (child, parent, affirm) ->
      {
        if (affirm) assignParent(parent, child);
        else        unassignParent(parent, child);
      });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  void addKeyWorkRelation(RecordType recordType, boolean forward)
  {
    recordTypes.addAll(EnumSet.of(recordType, hdtWork, hdtMiscFile));

    db.addKeyWorkHandler(recordType, forward ?
      (keyWork, record, affirm) ->
      {
        if (affirm) assignParent(keyWork, record);
        else        unassignParent(keyWork, record);
      }
    :
      (keyWork, record, affirm) ->
      {
        if (affirm) assignParent(record, keyWork);
        else        unassignParent(record, keyWork);
      });
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
