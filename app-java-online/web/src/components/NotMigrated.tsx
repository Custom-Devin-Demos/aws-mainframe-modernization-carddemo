import { useNavigate } from 'react-router-dom';
import { Screen } from './Screen';

/** Placeholder until the owning transaction group replaces it with the real page. */
export function NotMigrated({ program, tranId = '----' }: { program: string; tranId?: string }) {
  const navigate = useNavigate();
  return (
    <Screen
      tranId={tranId}
      program={program}
      title={`${program} has not been migrated yet`}
      pfKeys={[{ key: 'F3', label: 'Back', onPress: () => navigate(-1) }]}
    >
      <p>This page is a placeholder.</p>
    </Screen>
  );
}
