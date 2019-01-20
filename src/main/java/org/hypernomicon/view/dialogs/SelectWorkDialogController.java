/*
 * Copyright 2015-2019 Jason Winning
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

package org.hypernomicon.view.dialogs;

import static org.hypernomicon.view.wrappers.HyperTableColumn.HyperCtrlType.*;
import static org.hypernomicon.view.wrappers.HyperTableCell.*;

import org.hypernomicon.model.records.HDT_Base;
import org.hypernomicon.model.records.HDT_Person;
import org.hypernomicon.model.records.HDT_Work;

import static org.hypernomicon.model.records.HDT_RecordType.*;
import static org.hypernomicon.model.relations.RelationSet.RelationType.*;
import static org.hypernomicon.util.Util.*;
import static org.hypernomicon.util.Util.MessageDialogType.*;

import org.hypernomicon.view.populators.HybridSubjectPopulator;
import org.hypernomicon.view.populators.Populator;
import org.hypernomicon.view.populators.StandardPopulator;
import org.hypernomicon.view.wrappers.HyperCB;
import org.hypernomicon.view.wrappers.HyperTableCell;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

public class SelectWorkDialogController extends HyperDialog
{
  @FXML private ComboBox<HyperTableCell> cbAuthor;
  @FXML private ComboBox<HyperTableCell> cbWork;
  @FXML private Button btnOK;
  @FXML private Button btnCancel;

  private HyperCB hcbAuthor, hcbWork;

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public HDT_Work getWork() { return hcbWork.selectedRecord(); }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  public static SelectWorkDialogController create(String title, HDT_Person author)
  {
    SelectWorkDialogController swd = HyperDialog.create("SelectWorkDialog.fxml", title, true);
    swd.init(author);
    return swd;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  private void init(HDT_Person author)
  {
    hcbAuthor = new HyperCB(cbAuthor, ctDropDownList, new StandardPopulator(hdtPerson), null, false);
    hcbWork = new HyperCB(cbWork, ctDropDownList, new HybridSubjectPopulator(rtAuthorOfWork), null, false);

    cbAuthor.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
    {
      if (newValue == null) return;

      if (HyperTableCell.getCellID(oldValue) != HyperTableCell.getCellID(newValue))
      {
        ((HybridSubjectPopulator)hcbWork.getPopulator()).setObj(Populator.dummyRow, getRecord(newValue));
        hcbWork.selectID(-1);
      }
    });

    hcbAuthor.addAndSelectEntryOrBlank(author, HDT_Base::getCBText);

    hcbWork.addBlankEntry();
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

  @Override protected boolean isValid()
  {
    if (hcbWork.selectedID() < 1)
    {
      messageDialog("Select a work record.", mtInformation);
      safeFocus(cbWork);
      return false;
    }

    return true;
  }

//---------------------------------------------------------------------------
//---------------------------------------------------------------------------

}
