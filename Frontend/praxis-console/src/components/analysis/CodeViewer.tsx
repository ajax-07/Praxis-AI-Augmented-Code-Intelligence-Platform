import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo, useRef } from 'react';
import type { FindingView, Severity } from '../../types/api';
import { SEVERITY_HIGHLIGHT, severityRank } from '../../utils/severity';

/**
 * Lightweight code viewer: line numbers + per-line finding highlights.
 * (Deliberately not Monaco for the MVP — no 3 MB dependency for read-only
 * rendering; the seam to swap it in later is this one component.)
 */
export default function CodeViewer({
  source,
  findings,
  focusLine,
}: {
  source: string;
  findings: FindingView[];
  focusLine: number | null;
}) {
  const lines = useMemo(() => source.split(/\r?\n/), [source]);

  // Highest severity wins when findings overlap on a line.
  const lineSeverity = useMemo(() => {
    const map = new Map<number, Severity>();
    for (const f of findings) {
      if (f.startLine == null) continue;
      const end = f.endLine ?? f.startLine;
      for (let line = f.startLine; line <= end; line++) {
        const existing = map.get(line);
        if (!existing || severityRank(f.severity) < severityRank(existing)) {
          map.set(line, f.severity);
        }
      }
    }
    return map;
  }, [findings]);

  const focusRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    focusRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [focusLine]);

  return (
    <Box
      sx={{
        overflow: 'auto',
        height: '100%',
        fontFamily: '"Cascadia Code", Consolas, ui-monospace, monospace',
        fontSize: 13,
        lineHeight: 1.6,
      }}
    >
      {lines.map((text, i) => {
        const lineNo = i + 1;
        const severity = lineSeverity.get(lineNo);
        const isFocus = focusLine === lineNo;
        return (
          <Box
            key={lineNo}
            ref={isFocus ? focusRef : undefined}
            sx={{
              display: 'flex',
              bgcolor: severity ? SEVERITY_HIGHLIGHT[severity] : undefined,
              outline: isFocus ? '1px solid' : 'none',
              outlineColor: 'primary.main',
              px: 1,
            }}
          >
            <Typography
              component="span"
              sx={{
                width: 44,
                flexShrink: 0,
                textAlign: 'right',
                pr: 1.5,
                color: 'text.disabled',
                userSelect: 'none',
                font: 'inherit',
              }}
            >
              {lineNo}
            </Typography>
            <Typography component="pre" sx={{ m: 0, font: 'inherit', whiteSpace: 'pre' }}>
              {text || ' '}
            </Typography>
          </Box>
        );
      })}
    </Box>
  );
}
