import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { startAnalysis, uploadAnalysis } from '../api/analyses.api';
import type { StartAnalysisRequest } from '../types/api';

export function useStartAnalysis() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: StartAnalysisRequest) => startAnalysis(request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['analyses'] }),
  });
}

export function useUploadAnalysis() {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState(0);
  const mutation = useMutation({
    mutationFn: ({ file, name }: { file: File; name?: string }) =>
      uploadAnalysis(file, name, setProgress),
    onMutate: () => setProgress(0),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['analyses'] }),
  });
  return { ...mutation, progress };
}
