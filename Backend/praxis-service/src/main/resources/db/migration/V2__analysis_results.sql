-- Prism/Chronicle need to show a file's source in the dashboard, but the
-- fetched workspace is deleted after each run — so persist the source text.
ALTER TABLE file_result ADD COLUMN source TEXT;

-- Read-side indexes for the dashboard queries (files/findings by analysis).
CREATE INDEX IF NOT EXISTS idx_file_result_analysis ON file_result(analysis_id);
CREATE INDEX IF NOT EXISTS idx_code_unit_analysis   ON code_unit(analysis_id);
CREATE INDEX IF NOT EXISTS idx_issue_finding_analysis ON issue_finding(analysis_id);
CREATE INDEX IF NOT EXISTS idx_issue_finding_unit   ON issue_finding(code_unit_id);
