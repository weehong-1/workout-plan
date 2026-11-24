import { ExerciseDetail } from "@/components/ExerciseDetail";

type Props = { params: Promise<{ dayId: string; order: string }> };

export const metadata = { title: "Exercise" };

export default async function ExercisePage({ params }: Props) {
  const { dayId, order } = await params;
  return <ExerciseDetail dayId={dayId} order={decodeURIComponent(order)} />;
}
