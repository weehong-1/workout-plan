import { SaveWorkout } from "@/components/SaveWorkout";

type Props = { params: Promise<{ id: string }> };

export const metadata = { title: "Save workout" };

export default async function SavePage({ params }: Props) {
  const { id } = await params;
  return <SaveWorkout dayId={id} />;
}
