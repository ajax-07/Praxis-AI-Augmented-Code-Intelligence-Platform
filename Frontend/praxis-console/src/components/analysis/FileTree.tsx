import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import Collapse from '@mui/material/Collapse';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import { ChevronDown, ChevronRight, FileCode2, Folder } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { FileSummary } from '../../types/api';

interface DirNode {
  name: string;
  dirs: Map<string, DirNode>;
  files: FileSummary[];
}

/** Fold the flat path list from the API into a directory tree, sorted dirs-first. */
function buildTree(files: FileSummary[]): DirNode {
  const root: DirNode = { name: '', dirs: new Map(), files: [] };
  for (const file of files) {
    const parts = file.path.split('/');
    let node = root;
    for (const part of parts.slice(0, -1)) {
      let child = node.dirs.get(part);
      if (!child) {
        child = { name: part, dirs: new Map(), files: [] };
        node.dirs.set(part, child);
      }
      node = child;
    }
    node.files.push(file);
  }
  return root;
}

function DirEntry({
  node,
  depth,
  selectedId,
  onSelect,
}: {
  node: DirNode;
  depth: number;
  selectedId: string | null;
  onSelect: (file: FileSummary) => void;
}) {
  const [open, setOpen] = useState(true);
  return (
    <>
      <ListItemButton dense onClick={() => setOpen(!open)} sx={{ pl: 1 + depth * 1.5 }}>
        {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <Folder size={15} style={{ margin: '0 6px 0 4px', flexShrink: 0 }} />
        <ListItemText primary={node.name} primaryTypographyProps={{ variant: 'body2', noWrap: true }} />
      </ListItemButton>
      <Collapse in={open} unmountOnExit>
        <TreeLevel node={node} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
      </Collapse>
    </>
  );
}

function TreeLevel({
  node,
  depth,
  selectedId,
  onSelect,
}: {
  node: DirNode;
  depth: number;
  selectedId: string | null;
  onSelect: (file: FileSummary) => void;
}) {
  const dirs = [...node.dirs.values()].sort((a, b) => a.name.localeCompare(b.name));
  const files = [...node.files].sort((a, b) => a.path.localeCompare(b.path));
  return (
    <List disablePadding dense>
      {dirs.map((dir) => (
        <DirEntry key={dir.name} node={dir} depth={depth} selectedId={selectedId} onSelect={onSelect} />
      ))}
      {files.map((file) => (
        <ListItemButton
          key={file.fileResultId}
          dense
          selected={file.fileResultId === selectedId}
          onClick={() => onSelect(file)}
          sx={{ pl: 1 + depth * 1.5 + 2.5 }}
        >
          <FileCode2 size={15} style={{ marginRight: 6, flexShrink: 0 }} />
          <ListItemText
            primary={file.path.split('/').pop()}
            primaryTypographyProps={{ variant: 'body2', noWrap: true }}
          />
          {file.findingCount > 0 && (
            <Badge badgeContent={file.findingCount} color="warning" max={99} sx={{ mr: 1.5 }} />
          )}
        </ListItemButton>
      ))}
    </List>
  );
}

export default function FileTree({
  files,
  selectedId,
  onSelect,
}: {
  files: FileSummary[];
  selectedId: string | null;
  onSelect: (file: FileSummary) => void;
}) {
  const tree = useMemo(() => buildTree(files), [files]);
  return (
    <Box sx={{ overflowY: 'auto', height: '100%' }}>
      <TreeLevel node={tree} depth={0} selectedId={selectedId} onSelect={onSelect} />
    </Box>
  );
}
