package com.google.refine.operations.recon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.history.Change;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Row;
import com.google.refine.model.Recon.Judgment;
import com.google.refine.model.changes.CellChange;
import com.google.refine.model.changes.ReconChange;
import com.google.refine.operations.EngineDependentMassCellOperation;
import com.google.refine.operations.OperationRegistry;

public class ReconDiscardJudgmentsOperation extends EngineDependentMassCellOperation {
    static public AbstractOperation reconstruct(Project project, JSONObject obj) throws Exception {
        JSONObject engineConfig = obj.getJSONObject("engineConfig");
        String columnName = obj.getString("columnName");
        
        return new ReconDiscardJudgmentsOperation(
            engineConfig, 
            columnName
        );
    }
    
    public ReconDiscardJudgmentsOperation(JSONObject engineConfig, String columnName) {
        super(engineConfig, columnName, false);
    }

    public void write(JSONWriter writer, Properties options)
            throws JSONException {
        
        writer.object();
        writer.key("op"); writer.value(OperationRegistry.s_opClassToName.get(this.getClass()));
        writer.key("description"); writer.value(getBriefDescription(null));
        writer.key("engineConfig"); writer.value(getEngineConfig());
        writer.key("columnName"); writer.value(_columnName);
        writer.endObject();
    }

    protected String getBriefDescription(Project project) {
        return "Discard recon judgments for cells in column " + _columnName;
    }

    protected String createDescription(Column column,
            List<CellChange> cellChanges) {
        
        return "Discard recon judgments for " + cellChanges.size() + 
            " cells in column " + column.getName();
    }

    protected RowVisitor createRowVisitor(Project project, List<CellChange> cellChanges, long historyEntryID) throws Exception {
        Column column = project.columnModel.getColumnByName(_columnName);
        
        return new RowVisitor() {
            int cellIndex;
            List<CellChange> cellChanges;
            Map<Long, Recon> dupReconMap = new HashMap<Long, Recon>();
            long historyEntryID;
            
            public RowVisitor init(int cellIndex, List<CellChange> cellChanges, long historyEntryID) {
                this.cellIndex = cellIndex;
                this.cellChanges = cellChanges;
                this.historyEntryID = historyEntryID;
                return this;
            }
            
            @Override
            public void start(Project project) {
            	// nothing to do
            }
            
            @Override
            public void end(Project project) {
            	// nothing to do
            }
            
            public boolean visit(Project project, int rowIndex, Row row) {
                Cell cell = row.getCell(cellIndex);
                if (cell != null && cell.recon != null) {
                    Recon newRecon;
                    if (dupReconMap.containsKey(cell.recon.id)) {
                        newRecon = dupReconMap.get(cell.recon.id);
                        newRecon.judgmentBatchSize++;
                    } else {
                        newRecon = cell.recon.dup(historyEntryID);
                        newRecon.match = null;
                        newRecon.matchRank = -1;
                        newRecon.judgment = Judgment.None;
                        newRecon.judgmentAction = "mass";
                        newRecon.judgmentBatchSize = 1;
                        
                        dupReconMap.put(cell.recon.id, newRecon);
                    }
                    
                    Cell newCell = new Cell(cell.value, newRecon);
                    
                    CellChange cellChange = new CellChange(rowIndex, cellIndex, cell, newCell);
                    cellChanges.add(cellChange);
                }
                return false;
            }
        }.init(column.getCellIndex(), cellChanges, historyEntryID);
    }
    
    protected Change createChange(Project project, Column column, List<CellChange> cellChanges) {
        return new ReconChange(
            cellChanges, 
            _columnName, 
            column.getReconConfig(),
            null
        );
    }
}