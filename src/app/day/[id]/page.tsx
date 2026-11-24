import { WorkoutScreen } from "@/components/Workout";

type Props = { params: Promise<{ id: string }> };

export const metadata = { title: "Workout" };

export default async function DayPage({ params }: Props) {
  const { id } = await params;
  return <WorkoutScreen dayId={id} />;
}
