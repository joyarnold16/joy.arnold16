/*
 * The pose database.
 *
 * `avoid` is the machine-readable part and drives everything: it holds health
 * flags from YG.FLAGS, and any pose carrying a flag the user has set is either
 * swapped for its `alt` or dropped from the sequence. `cautions` is the prose
 * version shown on the pose card - the two are maintained together, and a flag
 * without a matching sentence is a bug, because a pose that silently vanishes
 * teaches the user nothing.
 *
 * Nothing here is medical advice, and the app says so on first launch. These
 * are the conventional contraindications taught with each posture; they are
 * deliberately cautious, because the cost of dropping a safe pose is a slightly
 * shorter session and the cost of keeping an unsafe one is an injury.
 *
 *   type   asana | pranayama | relaxation | mobility | kriya
 *   level  1 beginner, 2 intermediate, 3 advanced
 *   hold   default seconds (per side where `sides` is true)
 *   helps  condition ids from js/conditions.js
 *   alt    id of a gentler pose to substitute when this one is filtered out
 */
(function (YG) {
  'use strict';

  /* Health flags the profile screens for. Order is the order they are asked. */
  YG.FLAGS = [
    { id: 'pregnancy',      label: 'Pregnant' },
    { id: 'postnatal',      label: 'Recently gave birth (under 6 weeks)' },
    { id: 'hypertension',   label: 'High blood pressure' },
    { id: 'heart',          label: 'Heart condition' },
    { id: 'glaucoma',       label: 'Glaucoma or raised eye pressure' },
    { id: 'vertigo',        label: 'Vertigo or dizziness' },
    { id: 'epilepsy',       label: 'Epilepsy' },
    { id: 'hernia',         label: 'Hernia' },
    { id: 'ulcer',          label: 'Peptic ulcer or acid reflux' },
    { id: 'asthma',         label: 'Asthma or a reactive airway' },
    { id: 'disc',           label: 'Slipped disc or serious back injury' },
    { id: 'neck',           label: 'Neck injury' },
    { id: 'knee',           label: 'Knee problems' },
    { id: 'wrist',          label: 'Wrist pain or carpal tunnel' },
    { id: 'shoulder',       label: 'Shoulder injury' },
    { id: 'osteoporosis',   label: 'Osteoporosis' },
    { id: 'recent_surgery', label: 'Surgery in the last 3 months' },
    { id: 'menstruation',   label: 'Skip inversions during my period' }
  ];

  YG.POSES = [

    /* ============================ STANDING ============================ */

    {
      id: 'tadasana', name: 'Mountain Pose', sanskrit: 'Tadasana',
      type: 'asana', fig: 'stand', level: 1, hold: 40,
      breath: 'Breathe slowly and evenly through the nose.',
      steps: [
        'Stand with the feet hip-width apart, weight even across both soles.',
        'Let the arms hang beside the body, palms facing in.',
        'Lengthen up through the crown of the head; drop the shoulders down and back.',
        'Soften the jaw and breathe evenly.'
      ],
      benefits: ['Resets posture between other poses', 'Steadies balance and attention'],
      helps: ['back', 'desk', 'seniors', 'pregnancy', 'joints'],
      avoid: [], cautions: [],
      mistakes: ['Locking the knees back hard', 'Letting the ribs flare forward'],
      mods: ['Stand with the back against a wall if balance is unsteady.'],
      alt: null
    },
    {
      id: 'urdhva_hastasana', name: 'Upward Salute', sanskrit: 'Urdhva Hastasana',
      type: 'asana', fig: 'stand_arms_up', level: 1, hold: 30,
      breath: 'Inhale as the arms rise, exhale as they lower.',
      steps: [
        'From Mountain Pose, inhale and sweep both arms out and overhead.',
        'Reach the fingertips up without letting the shoulders climb to the ears.',
        'Keep the lower ribs drawing in so the lower back stays long.'
      ],
      benefits: ['Opens the chest and rib cage', 'Undoes hours of hunching forward'],
      helps: ['respiratory', 'desk', 'back', 'pregnancy'],
      avoid: ['shoulder'],
      cautions: ['With a shoulder injury, take the arms only as high as is painless.'],
      mistakes: ['Arching the lower back to get the arms higher'],
      mods: ['Hold a strap between the hands if the shoulders are tight.'],
      alt: 'shoulder_rolls'
    },
    {
      id: 'ardha_chakrasana', name: 'Standing Backbend', sanskrit: 'Ardha Chakrasana',
      type: 'asana', fig: 'backbend_stand', level: 2, hold: 20,
      breath: 'Inhale to lift and arch, exhale to come up.',
      steps: [
        'Stand tall, hands on the lower back, fingers pointing down.',
        'Inhale, lift the chest and arch gently backwards from the upper back.',
        'Keep the hips over the heels and the neck long.',
        'Exhale and come back to upright.'
      ],
      benefits: ['Expands the front of the chest and lungs', 'Counters a rounded upper back'],
      helps: ['respiratory', 'desk', 'thyroid'],
      avoid: ['hypertension', 'vertigo', 'disc', 'pregnancy'],
      cautions: [
        'Dizziness or high blood pressure: skip - dropping the head back changes pressure quickly.',
        'Late pregnancy: the balance shift makes this unsafe.'
      ],
      mistakes: ['Bending from the lower back instead of the upper back', 'Letting the head fall right back'],
      mods: ['Keep the gaze forward rather than up.'],
      alt: 'urdhva_hastasana'
    },
    {
      id: 'konasana', name: 'Standing Side Bend', sanskrit: 'Konasana',
      type: 'asana', fig: 'side_bend_stand', level: 1, hold: 25, sides: true,
      breath: 'Inhale to lengthen up, exhale to bend over.',
      steps: [
        'Stand with the feet a little wider than the hips, arms overhead.',
        'Exhale and bend to the right, keeping both feet grounded.',
        'Feel the stretch along the left side of the waist, not in the lower back.',
        'Inhale to come up; repeat on the other side.'
      ],
      benefits: ['Stretches the waist and the side of the rib cage', 'Encourages movement through the colon'],
      helps: ['digestion', 'desk', 'weight', 'pregnancy'],
      avoid: ['disc'],
      cautions: ['With a disc injury, side bending under load can aggravate it.'],
      mistakes: ['Leaning forward instead of directly sideways'],
      mods: ['One hand on the hip instead of overhead reduces the load.'],
      alt: 'seated_side_bend'
    },
    {
      id: 'uttanasana', name: 'Standing Forward Fold', sanskrit: 'Uttanasana',
      type: 'asana', fig: 'forward_fold', level: 1, hold: 30,
      breath: 'Exhale to fold, breathe softly while hanging.',
      steps: [
        'Stand with the feet hip-width apart.',
        'Exhale and hinge forward from the hips, knees softly bent.',
        'Let the head and arms hang heavy; hold opposite elbows if that is comfortable.',
        'Roll up slowly, head last.'
      ],
      benefits: ['Releases the hamstrings and lower back', 'Calms the nervous system'],
      helps: ['sleep', 'back', 'desk'],
      avoid: ['hypertension', 'glaucoma', 'vertigo', 'disc'],
      cautions: [
        'Head-below-heart raises pressure in the head and eyes - skip with high BP or glaucoma.',
        'Come up slowly always; standing up fast is what causes the head rush.'
      ],
      mistakes: ['Forcing the knees straight', 'Snapping upright at the end'],
      mods: ['Bend the knees generously - the fold comes from the hips, not the hamstrings.'],
      alt: 'balasana'
    },
    {
      id: 'utkatasana', name: 'Chair Pose', sanskrit: 'Utkatasana',
      type: 'asana', fig: 'utkatasana', level: 2, hold: 25,
      breath: 'Inhale to sit back, breathe steadily while holding.',
      steps: [
        'Stand with the feet together or hip-width apart.',
        'Exhale and bend the knees, sitting the hips back as if towards a chair.',
        'Keep the knees behind the toes and the weight in the heels.',
        'Raise the arms alongside the ears if the shoulders allow.'
      ],
      benefits: ['Builds thigh and hip strength', 'Raises the heart rate without impact'],
      helps: ['weight', 'joints', 'diabetes'],
      avoid: ['knee', 'hypertension'],
      cautions: ['Knee trouble: bend far less, or use the chair version instead.'],
      mistakes: ['Letting the knees travel past the toes', 'Rounding the lower back'],
      mods: ['Hold a wall or chair back for support; keep the arms at the chest.'],
      alt: 'chair_sit'
    },
    {
      id: 'vrikshasana', name: 'Tree Pose', sanskrit: 'Vrikshasana',
      type: 'asana', fig: 'vrikshasana', level: 2, hold: 30, sides: true,
      breath: 'Breathe evenly; a steady breath steadies the balance.',
      steps: [
        'Shift the weight onto the left foot.',
        'Place the right sole on the inner calf or inner thigh - never on the knee.',
        'Bring the palms together at the chest or overhead.',
        'Fix the gaze on one still point ahead. Switch sides.'
      ],
      benefits: ['Trains balance and ankle stability', 'Strengthens the standing leg'],
      helps: ['joints', 'seniors', 'desk', 'pregnancy'],
      avoid: ['vertigo'],
      cautions: ['With vertigo, keep one hand on a wall throughout.'],
      mistakes: ['Pressing the foot into the side of the standing knee'],
      mods: ['Toes on the floor with the heel at the ankle is a full version of the pose.',
             'Stand beside a wall - in pregnancy do this regardless, balance shifts week to week.'],
      alt: 'tadasana'
    },
    {
      id: 'virabhadrasana1', name: 'Warrior I', sanskrit: 'Virabhadrasana I',
      type: 'asana', fig: 'warrior1', level: 2, hold: 30, sides: true,
      breath: 'Inhale as the arms rise; breathe steadily in the hold.',
      steps: [
        'Step the left foot back about a leg length, turning it out slightly.',
        'Bend the right knee over the right ankle.',
        'Square the hips forward and lift the arms overhead.',
        'Hold, then switch sides.'
      ],
      benefits: ['Strengthens the legs and opens the hip flexors', 'Lifts and opens the chest'],
      helps: ['weight', 'back', 'respiratory', 'joints'],
      avoid: ['knee', 'hypertension', 'shoulder'],
      cautions: ['Arms overhead with high blood pressure: keep the hands at the hips instead.'],
      mistakes: ['Letting the front knee collapse inward', 'Over-arching the lower back'],
      mods: ['Shorten the stance; hands on the hips.'],
      alt: 'tadasana'
    },
    {
      id: 'virabhadrasana2', name: 'Warrior II', sanskrit: 'Virabhadrasana II',
      type: 'asana', fig: 'warrior2', level: 2, hold: 30, sides: true,
      breath: 'Breathe evenly and steadily throughout the hold.',
      steps: [
        'Step the feet wide, arms out at shoulder height.',
        'Turn the right foot out 90 degrees, the left foot slightly in.',
        'Bend the right knee towards a right angle, over the ankle.',
        'Look past the right hand. Switch sides.'
      ],
      benefits: ['Opens the hips', 'Builds stamina in the legs'],
      helps: ['weight', 'joints', 'pcos', 'pregnancy'],
      avoid: ['knee'],
      cautions: ['Knee trouble: bend the front knee only slightly.'],
      mistakes: ['Leaning the torso over the front leg'],
      mods: ['A shorter stance with less knee bend keeps the shape and drops the load.'],
      alt: 'tadasana'
    },
    {
      id: 'trikonasana', name: 'Triangle Pose', sanskrit: 'Trikonasana',
      type: 'asana', fig: 'trikonasana', level: 2, hold: 30, sides: true,
      breath: 'Exhale to extend over; breathe evenly in the hold.',
      steps: [
        'Step the feet wide, right foot turned out.',
        'Reach the right arm long over the right leg, then lower the hand to the shin or a block.',
        'Extend the left arm straight up, opening the chest.',
        'Hold, then switch sides.'
      ],
      benefits: ['Stretches the waist, hamstrings and side body', 'Massages the abdominal organs'],
      helps: ['digestion', 'back', 'weight', 'pcos'],
      avoid: ['disc', 'neck', 'vertigo'],
      cautions: ['With neck trouble, look down at the floor rather than up at the raised hand.'],
      mistakes: ['Collapsing the chest towards the floor', 'Reaching the hand so low the torso closes'],
      mods: ['Rest the lower hand on a block or on the shin, not the floor.'],
      alt: 'konasana'
    },
    {
      id: 'parsvakonasana', name: 'Extended Side Angle', sanskrit: 'Utthita Parsvakonasana',
      type: 'asana', fig: 'parsvakonasana', level: 2, hold: 30, sides: true,
      breath: 'Breathe into the stretched upper side.',
      steps: [
        'From Warrior II, lower the right forearm onto the right thigh.',
        'Sweep the left arm over the ear, palm facing down.',
        'Draw a long line from the back heel to the top fingertips.',
        'Hold, then switch sides.'
      ],
      benefits: ['Deep stretch through one whole side of the body', 'Stimulates digestion'],
      helps: ['digestion', 'weight', 'back'],
      avoid: ['knee', 'neck', 'disc'],
      cautions: ['Keep the gaze forward rather than up if the neck complains.'],
      mistakes: ['Dumping body weight into the forearm on the thigh'],
      mods: ['Hand on a block outside the front foot instead of forearm to thigh.'],
      alt: 'virabhadrasana2'
    },
    {
      id: 'prasarita', name: 'Wide-Leg Forward Fold', sanskrit: 'Prasarita Padottanasana',
      type: 'asana', fig: 'wide_fold', level: 2, hold: 35,
      breath: 'Exhale to fold, then let the breath settle.',
      steps: [
        'Step the feet wide, toes pointing forward, hands on the hips.',
        'Inhale to lengthen the spine, exhale and hinge forward from the hips.',
        'Let the hands come to the floor or a block, crown of the head heavy.',
        'Come up with a flat back.'
      ],
      benefits: ['Releases the hamstrings and lower back', 'Quietens a busy mind'],
      helps: ['sleep', 'back', 'migraine'],
      avoid: ['hypertension', 'glaucoma', 'disc', 'vertigo'],
      cautions: ['Head below the heart: skip with high BP, glaucoma or dizziness.'],
      mistakes: ['Rounding from the waist rather than hinging at the hips'],
      mods: ['Rest the hands on a chair seat so the head stays above the heart.'],
      alt: 'balasana'
    },
    {
      id: 'malasana', name: 'Garland Pose (Deep Squat)', sanskrit: 'Malasana',
      type: 'asana', fig: 'malasana', level: 2, hold: 40,
      breath: 'Breathe down into the belly and pelvic floor.',
      steps: [
        'Stand with the feet a little wider than the hips, toes turned slightly out.',
        'Bend the knees and lower the hips towards the floor.',
        'Bring the palms together and press the elbows lightly against the inner knees.',
        'Keep the spine long and the heels down if they reach.'
      ],
      benefits: ['Opens the hips and pelvic floor', 'One of the most effective postures for a sluggish bowel'],
      helps: ['digestion', 'pregnancy', 'pcos', 'back'],
      avoid: ['knee', 'recent_surgery'],
      cautions: [
        'Knee problems: sit on a block or a low stool instead of squatting deep.',
        'Late pregnancy with a low-lying baby or any bleeding: check with your midwife first.'
      ],
      mistakes: ['Letting the lower back round hard', 'Forcing the heels down'],
      mods: ['Sit on a folded blanket or a block under the seat.',
             'Hold a door frame or the back of a chair for support.'],
      alt: 'baddha_konasana'
    },
    {
      id: 'garudasana', name: 'Eagle Arms', sanskrit: 'Garudasana (arms)',
      type: 'asana', fig: 'garudasana', level: 2, hold: 25, sides: true,
      breath: 'Breathe wide into the upper back.',
      steps: [
        'Cross the right arm under the left at the elbows.',
        'Wind the forearms and bring the palms towards each other.',
        'Lift the elbows to shoulder height and press the hands away from the face.',
        'Switch which arm is on top.'
      ],
      benefits: ['Opens between the shoulder blades', 'Relieves upper-back tightness from screen work'],
      helps: ['desk', 'respiratory', 'joints'],
      avoid: ['shoulder'],
      cautions: ['With a shoulder injury, cross the arms and hold opposite shoulders instead.'],
      mistakes: ['Hunching the shoulders up towards the ears'],
      mods: ['Hug yourself, hands on opposite shoulders, for the same upper-back opening.'],
      alt: 'shoulder_rolls'
    },
    {
      id: 'natarajasana', name: 'Dancer Pose', sanskrit: 'Natarajasana',
      type: 'asana', fig: 'natarajasana', level: 3, hold: 20, sides: true,
      breath: 'Steady, even breath - holding the breath topples the balance.',
      steps: [
        'Stand tall and shift the weight into the left foot.',
        'Bend the right knee and catch the right ankle behind you.',
        'Press the foot into the hand and lean the chest slightly forward.',
        'Extend the left arm ahead. Switch sides.'
      ],
      benefits: ['Balance, focus and a strong chest opening', 'Stretches the front of the thigh'],
      helps: ['joints', 'back', 'respiratory'],
      avoid: ['vertigo', 'knee', 'disc', 'pregnancy', 'hypertension'],
      cautions: ['A demanding balance with a backbend - build up to it rather than starting here.'],
      mistakes: ['Letting the standing knee lock and wobble'],
      mods: ['Hold a wall with the free hand.', 'Use a strap around the lifted foot.'],
      alt: 'vrikshasana'
    },
    {
      id: 'wall_chest_opener', name: 'Wall Chest Opener', sanskrit: '',
      type: 'asana', fig: 'wall_stand', level: 1, hold: 30, sides: true,
      breath: 'Breathe slowly into the opened side of the chest.',
      steps: [
        'Stand beside a wall and place one palm flat on it at shoulder height.',
        'Keeping the hand still, turn the chest slowly away from the wall.',
        'Stop where you feel a broad stretch across the front of the chest, not a pull in the shoulder.',
        'Hold, then change sides.'
      ],
      benefits: ['Opens the pectorals that close down over a keyboard', 'Makes fuller breathing possible'],
      helps: ['desk', 'respiratory', 'seniors', 'pregnancy'],
      avoid: ['shoulder'],
      cautions: ['Any pinching in the front of the shoulder means you have turned too far.'],
      mistakes: ['Turning so far the shoulder takes the strain instead of the chest'],
      mods: ['Lower the hand to hip height for a gentler angle.'],
      alt: 'shoulder_rolls'
    },

    /* ============================= SEATED ============================= */

    {
      id: 'sukhasana', name: 'Easy Seated Pose', sanskrit: 'Sukhasana',
      type: 'asana', fig: 'sukhasana', level: 1, hold: 60,
      breath: 'Slow nasal breathing, exhale a little longer than the inhale.',
      steps: [
        'Sit cross-legged on the floor or on a folded blanket.',
        'Stack the head over the shoulders, shoulders over the hips.',
        'Rest the hands on the knees, palms up or down.',
        'Let the face and jaw soften.'
      ],
      benefits: ['The base position for most breathing practice', 'Teaches an upright, unforced spine'],
      helps: ['sleep', 'pregnancy', 'seniors', 'migraine', 'desk'],
      avoid: [], cautions: [],
      mistakes: ['Sitting flat on the floor with the pelvis tipped back and the spine rounded'],
      mods: ['Sit on a cushion high enough that the knees are below the hips.',
             'Sit on a chair with both feet flat - every seated practice works there too.'],
      alt: 'chair_sit'
    },
    {
      id: 'padmasana', name: 'Lotus Pose', sanskrit: 'Padmasana',
      type: 'asana', fig: 'padmasana', level: 3, hold: 60,
      breath: 'Slow, quiet nasal breath.',
      steps: [
        'From sitting, place the right foot high on the left thigh.',
        'Then the left foot high on the right thigh, if the hips allow.',
        'Rest the hands on the knees and lengthen the spine.',
        'Come out well before the legs go numb.'
      ],
      benefits: ['A very stable seat for long meditation'],
      helps: ['sleep'],
      avoid: ['knee', 'recent_surgery'],
      cautions: ['Full Lotus asks a lot of the knees. If the hips are tight the knee takes the rotation - and that is how knees get injured. Half Lotus or Sukhasana is not a lesser pose.'],
      mistakes: ['Forcing the second foot up when the hips are not open'],
      mods: ['Half Lotus: one foot up, one foot underneath.', 'Sukhasana is a complete substitute.'],
      alt: 'sukhasana'
    },
    {
      id: 'vajrasana', name: 'Thunderbolt Pose', sanskrit: 'Vajrasana',
      type: 'asana', fig: 'vajrasana', level: 1, hold: 90,
      breath: 'Easy natural breathing.',
      steps: [
        'Kneel with the shins on the floor and the big toes touching.',
        'Sit back onto the heels.',
        'Rest the palms on the thighs and lengthen the spine.',
        'Breathe quietly.'
      ],
      benefits: ['The one asana traditionally done just after eating - it aids digestion rather than disturbing it',
                 'A steady, alert seat'],
      helps: ['digestion', 'pregnancy', 'seniors', 'diabetes'],
      avoid: ['knee'],
      cautions: ['Knee or ankle problems: place a cushion between the seat and the heels, or sit on a chair.'],
      mistakes: ['Staying so long the feet go numb'],
      mods: ['A folded blanket between calves and thighs takes most of the pressure off the knees.'],
      alt: 'sukhasana'
    },
    {
      id: 'baddha_konasana', name: 'Butterfly Pose', sanskrit: 'Baddha Konasana',
      type: 'asana', fig: 'baddha_konasana', level: 1, hold: 60,
      breath: 'Breathe slowly, letting the inner thighs release on each exhale.',
      steps: [
        'Sit with the soles of the feet together, heels a comfortable distance from the body.',
        'Hold the feet or ankles and sit tall.',
        'Let the knees fall towards the floor under their own weight.',
        'Optionally flutter the knees gently for a few breaths.'
      ],
      benefits: ['Opens the hips and inner thighs', 'A staple of prenatal practice - prepares the pelvis'],
      helps: ['pcos', 'pregnancy', 'digestion', 'postnatal', 'back', 'joints'],
      avoid: ['knee'],
      cautions: ['Do not press the knees down with the hands - let gravity do it.'],
      mistakes: ['Rounding the back to bring the feet closer'],
      mods: ['Sit on a cushion; support each knee on a folded blanket or block.'],
      alt: 'sukhasana'
    },
    {
      id: 'upavistha_konasana', name: 'Wide-Angle Seated Pose', sanskrit: 'Upavistha Konasana',
      type: 'asana', fig: 'upavistha_konasana', level: 2, hold: 45,
      breath: 'Exhale to walk the hands forward, inhale to lengthen.',
      steps: [
        'Sit with the legs wide apart, kneecaps and toes pointing up.',
        'Sit tall on the sitting bones.',
        'Walk the hands forward only as far as the spine stays long.',
        'Come up slowly.'
      ],
      benefits: ['Stretches the inner thighs and hamstrings', 'Opens the pelvis'],
      helps: ['pcos', 'back', 'pregnancy', 'joints'],
      avoid: ['disc'],
      cautions: ['Stop the forward walk the moment the lower back starts to round.'],
      mistakes: ['Letting the knees roll inward'],
      mods: ['Sit on a cushion and keep the torso upright - no forward bend at all.'],
      alt: 'baddha_konasana'
    },
    {
      id: 'paschimottanasana', name: 'Seated Forward Bend', sanskrit: 'Paschimottanasana',
      type: 'asana', fig: 'paschimottanasana', level: 2, hold: 45,
      breath: 'Inhale to lengthen the spine, exhale to fold a little further.',
      steps: [
        'Sit with the legs straight ahead, feet flexed.',
        'Inhale and raise the arms, lengthening the spine.',
        'Exhale and hinge forward from the hips, hands to the shins, ankles or feet.',
        'Let the head be the last thing to relax down.'
      ],
      benefits: ['Compresses and then releases the abdomen, which stimulates digestion',
                 'Long stretch through the whole back line of the body'],
      helps: ['digestion', 'diabetes', 'sleep', 'thyroid', 'back'],
      avoid: ['pregnancy', 'disc', 'hernia'],
      cautions: ['In pregnancy a closed forward fold compresses the belly - use the wide-legged version instead.'],
      mistakes: ['Yanking on the feet and rounding the whole spine', 'Straining to touch the toes'],
      mods: ['Bend the knees generously; loop a strap around the feet.'],
      alt: 'upavistha_konasana'
    },
    {
      id: 'janu_sirsasana', name: 'Head-to-Knee Pose', sanskrit: 'Janu Sirsasana',
      type: 'asana', fig: 'janu_sirsasana', level: 2, hold: 40, sides: true,
      breath: 'Exhale into the fold; keep the breath moving.',
      steps: [
        'Sit with the right leg straight, left sole against the right inner thigh.',
        'Turn the chest to face over the right leg.',
        'Inhale to lengthen, exhale to fold towards the right foot.',
        'Change sides.'
      ],
      benefits: ['Gentler on the back than a two-legged fold', 'Massages the abdomen on one side at a time'],
      helps: ['digestion', 'diabetes', 'back', 'pcos'],
      avoid: ['disc', 'knee'],
      cautions: ['Knee problems: support the bent knee on a cushion.'],
      mistakes: ['Twisting the torso instead of squaring it over the straight leg'],
      mods: ['Strap around the extended foot; sit up on a folded blanket.'],
      alt: 'dandasana'
    },
    {
      id: 'ardha_matsyendrasana', name: 'Half Spinal Twist', sanskrit: 'Ardha Matsyendrasana',
      type: 'asana', fig: 'ardha_matsyendrasana', level: 2, hold: 30, sides: true,
      breath: 'Inhale to lengthen up, exhale to twist a little further.',
      steps: [
        'Sit with the legs straight, then bend the right knee and step the foot outside the left thigh.',
        'Fold the left leg in, or leave it straight.',
        'Inhale and lengthen the spine; exhale and turn to the right.',
        'Hold, unwind slowly, then change sides.'
      ],
      benefits: ['Wrings out the abdominal organs - one of the most reliable poses for a stuck bowel',
                 'Restores rotation to a stiff spine'],
      helps: ['digestion', 'diabetes', 'back', 'thyroid', 'pcos', 'desk'],
      avoid: ['pregnancy', 'disc', 'hernia', 'recent_surgery'],
      cautions: [
        'In pregnancy, deep closed twists compress the abdomen - twist gently and only from the upper back, away from the front knee.',
        'Recent abdominal surgery: leave twists out entirely until cleared.'
      ],
      mistakes: ['Cranking the twist with the arm instead of turning from the spine',
                 'Letting the sitting bones lift off the floor'],
      mods: ['Keep the lower leg straight; twist only as far as the breath stays easy.'],
      alt: 'seated_side_bend'
    },
    {
      id: 'gomukhasana', name: 'Cow Face Arms', sanskrit: 'Gomukhasana (arms)',
      type: 'asana', fig: 'gomukhasana', level: 2, hold: 30, sides: true,
      breath: 'Breathe into the stretched shoulder.',
      steps: [
        'Sit comfortably. Reach the right arm up, bend the elbow, hand behind the head.',
        'Bring the left arm behind the back, hand climbing up between the shoulder blades.',
        'Link the fingers if they meet, or hold a strap between them.',
        'Change sides.'
      ],
      benefits: ['Opens the chest, shoulders and armpits', 'Improves the mobility needed for full inhalation'],
      helps: ['desk', 'respiratory', 'joints'],
      avoid: ['shoulder'],
      cautions: ['Never force the hands to meet - use a strap and let the shoulder decide.'],
      mistakes: ['Pulling the head forward with the top hand'],
      mods: ['Hold a strap or a towel between the hands.'],
      alt: 'shoulder_rolls'
    },
    {
      id: 'dandasana', name: 'Staff Pose', sanskrit: 'Dandasana',
      type: 'asana', fig: 'dandasana', level: 1, hold: 40,
      breath: 'Even, quiet breathing.',
      steps: [
        'Sit with both legs straight out in front, feet flexed.',
        'Press the hands into the floor beside the hips.',
        'Lift the chest and lengthen the spine as if against a wall.',
        'Hold, breathing evenly.'
      ],
      benefits: ['Teaches an upright seated spine', 'Wakes up the postural muscles of the back'],
      helps: ['back', 'desk', 'seniors', 'joints'],
      avoid: [], cautions: [],
      mistakes: ['Letting the pelvis tip back so the lower back rounds'],
      mods: ['Sit on a folded blanket; bend the knees slightly.'],
      alt: 'chair_sit'
    },
    {
      id: 'seated_side_bend', name: 'Seated Side Stretch', sanskrit: 'Parsva Sukhasana',
      type: 'asana', fig: 'seated_side_bend', level: 1, hold: 30, sides: true,
      breath: 'Inhale to lengthen, exhale to lean over.',
      steps: [
        'Sit cross-legged, left hand resting on the floor beside the hip.',
        'Inhale the right arm up and over towards the left.',
        'Keep both sitting bones heavy on the floor.',
        'Come up on an inhale and change sides.'
      ],
      benefits: ['Opens the side ribs so the lungs can expand further', 'Safe in every trimester'],
      helps: ['desk', 'digestion', 'pregnancy', 'respiratory', 'seniors'],
      avoid: [], cautions: [],
      mistakes: ['Letting the opposite hip lift off the floor'],
      mods: ['Do it seated on a chair with a hand on the seat edge.'],
      alt: 'chair_sit'
    },

    /* ======================== HANDS AND KNEES ========================= */

    {
      id: 'marjari_bitilasana', name: 'Cat-Cow', sanskrit: 'Marjaryasana-Bitilasana',
      type: 'asana', fig: 'cow', fig2: 'cat', level: 1, hold: 60,
      breath: 'Inhale as the belly drops and the chest lifts; exhale as the back rounds.',
      steps: [
        'Come to hands and knees, wrists under shoulders, knees under hips.',
        'Inhale: drop the belly, lift the tailbone and chest (Cow).',
        'Exhale: round the spine, tuck the chin and tailbone (Cat).',
        'Move with the breath, one round per breath.'
      ],
      benefits: ['Restores movement to every segment of the spine',
                 'The single most useful movement in pregnancy - relieves back load and encourages good baby position'],
      helps: ['back', 'pregnancy', 'postnatal', 'digestion', 'desk', 'seniors'],
      avoid: ['wrist', 'knee'],
      cautions: ['Wrist pain: come onto the forearms or make fists.'],
      mistakes: ['Forcing the range at the ends instead of moving evenly through the spine'],
      mods: ['Pad the knees with a folded blanket.',
             'Do it seated on a chair - same movement, hands on the knees.'],
      alt: 'chair_sit'
    },
    {
      id: 'balasana', name: "Child's Pose", sanskrit: 'Balasana',
      type: 'asana', fig: 'balasana', level: 1, hold: 60,
      breath: 'Breathe into the back of the rib cage.',
      steps: [
        'Kneel, big toes together, knees as wide as is comfortable.',
        'Sit the hips back towards the heels.',
        'Walk the hands forward and rest the forehead down.',
        'Let the whole back soften.'
      ],
      benefits: ['The standard resting pose between stronger postures', 'Quietly releases the lower back'],
      helps: ['back', 'sleep', 'migraine', 'digestion', 'desk', 'pcos'],
      avoid: ['knee'],
      cautions: ['In pregnancy take the knees very wide so there is no pressure on the belly - and skip it if it feels crowded at all.'],
      mistakes: ['Keeping the knees together when the belly or chest needs room'],
      mods: ['Cushion between the seat and heels; a bolster under the chest.'],
      alt: 'chair_fold'
    },
    {
      id: 'adho_mukha', name: 'Downward-Facing Dog', sanskrit: 'Adho Mukha Svanasana',
      type: 'asana', fig: 'adho_mukha', level: 2, hold: 30,
      breath: 'Steady, even breath; do not hold it.',
      steps: [
        'From hands and knees, tuck the toes and lift the hips up and back.',
        'Keep the knees bent at first; lengthen the spine before straightening the legs.',
        'Press the hands evenly and let the head hang between the arms.',
        'Come down to the knees to finish.'
      ],
      benefits: ['Full-body stretch that also opens the chest', 'Builds shoulder and arm strength'],
      helps: ['back', 'respiratory', 'weight', 'desk'],
      avoid: ['hypertension', 'glaucoma', 'wrist', 'vertigo', 'pregnancy'],
      cautions: [
        'Head below the heart: skip with high BP, glaucoma or dizziness.',
        'Later pregnancy: the wrist load and the position of the belly both make this a poor choice.'
      ],
      mistakes: ['Straightening the legs at the cost of a rounded spine'],
      mods: ['Hands on a chair seat or a wall - the same shape at half the load.'],
      alt: 'balasana'
    },
    {
      id: 'ustrasana', name: 'Camel Pose', sanskrit: 'Ustrasana',
      type: 'asana', fig: 'ustrasana', level: 3, hold: 20,
      breath: 'Inhale to lift the chest before you go back; breathe evenly in the pose.',
      steps: [
        'Kneel with the knees hip-width apart, hands on the lower back.',
        'Inhale, lift the chest and draw the shoulder blades together.',
        'Arch back, reaching for the heels only if the chest stays lifted.',
        'Come up leading with the chest, head last. Rest in Child\'s Pose.'
      ],
      benefits: ['Strong opening across the chest, throat and front of the body',
                 'Stimulates the thyroid through the throat stretch'],
      helps: ['respiratory', 'thyroid', 'desk'],
      avoid: ['hypertension', 'neck', 'disc', 'hernia', 'pregnancy'],
      cautions: ['A strong backbend. Never drop the head back if the neck is at all sensitive.',
                 'If you are prone to migraine, the throat and neck extension can be a trigger - go lightly.'],
      mistakes: ['Pushing the hips back behind the knees', 'Leading with the head instead of the chest'],
      mods: ['Keep the hands on the lower back and arch only slightly.',
             'Tuck the toes under to raise the heels closer.'],
      alt: 'ardha_chakrasana'
    },
    {
      id: 'simhasana', name: 'Lion Pose', sanskrit: 'Simhasana',
      type: 'asana', fig: 'simhasana', level: 1, hold: 30,
      breath: 'Inhale through the nose; exhale forcefully through the open mouth with a "haa".',
      steps: [
        'Kneel or sit comfortably, hands on the knees, fingers spread.',
        'Inhale deeply through the nose.',
        'Open the mouth wide, stick the tongue out and down, and exhale with an audible "haa".',
        'Repeat five or six times.'
      ],
      benefits: ['Stretches and stimulates the throat and thyroid area', 'Releases jaw and facial tension'],
      helps: ['thyroid', 'respiratory', 'immunity'],
      avoid: [], cautions: [],
      mistakes: ['Doing it half-heartedly - the strong exhale is the practice'],
      mods: ['Sit on a chair; keep the gaze forward rather than up.'],
      alt: 'sukhasana'
    },

    /* ============================= PRONE ============================== */

    {
      id: 'bhujangasana', name: 'Cobra Pose', sanskrit: 'Bhujangasana',
      type: 'asana', fig: 'bhujangasana', level: 1, hold: 25,
      breath: 'Inhale to lift, breathe evenly in the hold, exhale to lower.',
      steps: [
        'Lie face down, hands under the shoulders, elbows close to the body.',
        'Press the tops of the feet and the pubic bone into the floor.',
        'Inhale and peel the chest up, using the back more than the arms.',
        'Keep the shoulders down and the neck long. Exhale to lower.'
      ],
      benefits: ['Opens the chest and increases lung capacity',
                 'Stretches the abdomen, which stimulates a sluggish digestive tract'],
      helps: ['respiratory', 'digestion', 'back', 'thyroid', 'pcos'],
      avoid: ['pregnancy', 'hernia', 'ulcer', 'recent_surgery', 'disc', 'wrist'],
      cautions: [
        'Any prone pose is out in pregnancy after the first trimester.',
        'With a hernia or ulcer, the abdominal stretch is the problem, not the backbend.'
      ],
      mistakes: ['Pushing up on straight arms so the lower back takes all the bend',
                 'Throwing the head back'],
      mods: ['Sphinx: rest on the forearms instead of the hands.'],
      alt: 'marjari_bitilasana'
    },
    {
      id: 'salabhasana', name: 'Locust Pose', sanskrit: 'Salabhasana',
      type: 'asana', fig: 'salabhasana', level: 2, hold: 20,
      breath: 'Inhale to lift, breathe steadily, exhale to release.',
      steps: [
        'Lie face down, arms alongside the body, palms down.',
        'Inhale and lift the chest, arms and both legs off the floor.',
        'Keep the neck in line with the spine, gaze down.',
        'Lower on an exhale and rest a breath before repeating.'
      ],
      benefits: ['Strengthens the whole back of the body', 'Firms and stimulates the abdomen'],
      helps: ['back', 'digestion', 'pcos', 'weight'],
      avoid: ['pregnancy', 'hernia', 'ulcer', 'hypertension', 'neck', 'disc', 'heart'],
      cautions: ['A strong effort against gravity - skip with heart trouble or high blood pressure.'],
      mistakes: ['Lifting the chin to get higher'],
      mods: ['Half Locust: lift one leg at a time with the chest down.'],
      alt: 'marjari_bitilasana'
    },
    {
      id: 'dhanurasana', name: 'Bow Pose', sanskrit: 'Dhanurasana',
      type: 'asana', fig: 'dhanurasana', level: 3, hold: 20,
      breath: 'Inhale to lift; breathe evenly - the belly rocks with each breath, which is part of the effect.',
      steps: [
        'Lie face down, bend both knees and catch the ankles.',
        'Inhale, kick the feet back into the hands and lift the chest and thighs.',
        'Keep the knees roughly hip-width apart.',
        'Release on an exhale and rest.'
      ],
      benefits: ['Full front-body opening', 'The rocking pressure on the abdomen is strongly digestive'],
      helps: ['digestion', 'respiratory', 'thyroid', 'pcos', 'diabetes'],
      avoid: ['pregnancy', 'hypertension', 'hernia', 'ulcer', 'neck', 'disc', 'heart', 'recent_surgery'],
      cautions: ['One of the strongest backbends in a general practice - build up through Cobra and Locust first.'],
      mistakes: ['Grabbing the feet rather than the ankles and cramping the hamstrings'],
      mods: ['Half Bow: one side at a time.', 'Loop a strap around each ankle.'],
      alt: 'bhujangasana'
    },
    {
      id: 'makarasana', name: 'Crocodile Pose', sanskrit: 'Makarasana',
      type: 'relaxation', fig: 'makarasana', level: 1, hold: 90,
      breath: 'Let the belly press into the floor on each inhale - it makes diaphragmatic breathing obvious.',
      steps: [
        'Lie face down and stack the forearms in front of you.',
        'Rest the forehead or one cheek on the forearms.',
        'Let the legs fall open, heels turning out.',
        'Feel the belly press the floor as you inhale.'
      ],
      benefits: ['Teaches diaphragmatic breathing better than any instruction can',
                 'A resting position that also releases the lower back'],
      helps: ['back', 'sleep', 'respiratory'],
      avoid: ['pregnancy'],
      cautions: ['Lying face down is not an option once the belly has grown - use side-lying rest instead.'],
      mistakes: ['Propping the chest so high the lower back compresses'],
      mods: ['A folded blanket under the hips softens the lower back.'],
      alt: 'side_lying_rest'
    },

    /* ============================= SUPINE ============================= */

    {
      id: 'savasana', name: 'Corpse Pose', sanskrit: 'Savasana',
      type: 'relaxation', fig: 'savasana', level: 1, hold: 180,
      breath: 'Let the breath find its own rhythm - do not steer it.',
      steps: [
        'Lie on the back, legs a little apart, feet falling open.',
        'Arms slightly away from the body, palms up.',
        'Close the eyes and let the whole body be heavy.',
        'Stay still. Come out by rolling onto the right side first.'
      ],
      benefits: ['Where the effects of the practice actually settle',
                 'Drops heart rate and blood pressure'],
      helps: ['sleep', 'hypertension', 'migraine', 'immunity', 'desk', 'seniors'],
      avoid: ['pregnancy'],
      cautions: ['After about 16 weeks of pregnancy, lying flat on the back can compress a major vein and make you feel faint - lie on the left side instead.'],
      mistakes: ['Cutting it short - this is the pose, not the cool-down'],
      mods: ['Bolster under the knees to protect the lower back.',
             'Lie on the left side with a pillow between the knees.'],
      alt: 'side_lying_rest'
    },
    {
      id: 'setu_bandhasana', name: 'Bridge Pose', sanskrit: 'Setu Bandhasana',
      type: 'asana', fig: 'setu_bandhasana', level: 1, hold: 30,
      breath: 'Inhale to lift, breathe evenly in the hold, exhale to lower slowly.',
      steps: [
        'Lie on the back, knees bent, feet hip-width apart close to the hips.',
        'Press through the feet and lift the hips.',
        'Keep the knees parallel and the chin slightly away from the chest.',
        'Lower one vertebra at a time.'
      ],
      benefits: ['Opens the chest and the front of the hips', 'Strengthens the back and glutes'],
      helps: ['back', 'thyroid', 'sleep', 'pcos', 'respiratory', 'postnatal'],
      avoid: ['neck', 'disc'],
      cautions: [
        'Never turn the head while the hips are lifted - the neck is loaded.',
        'In pregnancy keep the lift low and come down if you feel any pressure.'
      ],
      mistakes: ['Letting the knees splay outwards', 'Pushing the hips so high the lower back pinches'],
      mods: ['Supported bridge: slide a block under the sacrum and rest there.'],
      alt: 'pelvic_tilts'
    },
    {
      id: 'matsyasana', name: 'Fish Pose', sanskrit: 'Matsyasana',
      type: 'asana', fig: 'matsyasana', level: 2, hold: 25,
      breath: 'Slow, deep breaths into the very top of the chest.',
      steps: [
        'Lie on the back, hands tucked palms-down under the hips.',
        'Press the forearms down and lift the chest.',
        'Let the crown of the head rest lightly on the floor - almost no weight on it.',
        'Lift the head first to come out.'
      ],
      benefits: ['Opens the very top of the lungs, which most breathing never reaches',
                 'Strong stretch across the throat'],
      helps: ['respiratory', 'thyroid', 'desk'],
      avoid: ['neck', 'hypertension', 'pregnancy', 'vertigo'],
      cautions: ['The weight belongs in the forearms, not the head. If the neck feels compressed, come out.'],
      mistakes: ['Resting real body weight on the crown of the head'],
      mods: ['Lie lengthways along a bolster instead - same chest opening, no neck load.'],
      alt: 'supta_baddha_konasana'
    },
    {
      id: 'pawanmuktasana', name: 'Wind-Relieving Pose', sanskrit: 'Pawanmuktasana',
      type: 'asana', fig: 'pawanmuktasana', level: 1, hold: 40, sides: true,
      breath: 'Exhale as you draw the knee in - the exhale is what deepens the pressure.',
      steps: [
        'Lie on the back. Exhale and draw the right knee towards the chest.',
        'Hold the shin and keep the left leg long on the floor.',
        'Hold for several breaths, squeezing a little more on each exhale.',
        'Change sides, then hug both knees in together.'
      ],
      benefits: ['Named for exactly what it does - applies direct pressure along the colon',
                 'Releases the lower back'],
      helps: ['digestion', 'back', 'postnatal'],
      avoid: ['pregnancy', 'recent_surgery', 'hernia', 'disc'],
      cautions: ['Direct pressure on the abdomen: not in pregnancy, and not after recent abdominal surgery.'],
      mistakes: ['Lifting the head and straining the neck'],
      mods: ['Hold behind the thigh instead of over the shin if the knee is sore.'],
      alt: 'marjari_bitilasana'
    },
    {
      id: 'supta_baddha_konasana', name: 'Reclined Butterfly', sanskrit: 'Supta Baddha Konasana',
      type: 'relaxation', fig: 'supta_baddha_konasana', level: 1, hold: 120,
      breath: 'Slow and quiet; let each exhale soften the hips further.',
      steps: [
        'Lie back, ideally with a bolster or cushions along the spine.',
        'Bring the soles of the feet together and let the knees fall open.',
        'Support each knee on a cushion so nothing has to hold itself up.',
        'Rest the arms open, palms up, and stay.'
      ],
      benefits: ['Deeply restorative hip opening that requires no effort',
                 'A reliable way to settle an agitated nervous system'],
      helps: ['pcos', 'sleep', 'pregnancy', 'hypertension', 'postnatal', 'migraine'],
      avoid: ['knee'],
      cautions: ['In pregnancy, prop the upper body well so you are reclining rather than lying flat.'],
      mistakes: ['Leaving the knees unsupported, so the hips stay working'],
      mods: ['The more props the better - this pose should feel like doing nothing.'],
      alt: 'side_lying_rest'
    },
    {
      id: 'viparita_karani', name: 'Legs Up the Wall', sanskrit: 'Viparita Karani',
      type: 'relaxation', fig: 'viparita_karani', level: 1, hold: 180,
      breath: 'Natural, unforced breathing.',
      steps: [
        'Sit sideways with one hip against a wall.',
        'Swing the legs up the wall as you lie down on your back.',
        'Shuffle the hips as close to the wall as is comfortable.',
        'Rest the arms out to the sides and stay for several minutes.'
      ],
      benefits: ['Drains tired, swollen legs', 'One of the most dependable poses for winding down before sleep'],
      helps: ['sleep', 'back', 'migraine', 'pcos', 'seniors', 'hypertension'],
      avoid: ['glaucoma', 'menstruation', 'pregnancy'],
      cautions: [
        'A mild inversion: skip with glaucoma, and during your period if you follow that convention.',
        'In pregnancy, use the legs-on-a-chair version so you are not flat on your back.'
      ],
      mistakes: ['Forcing the hips right against the wall when the hamstrings are tight'],
      mods: ['Move the hips a foot away from the wall.', 'Rest the calves on a chair seat instead.'],
      alt: 'legs_on_chair'
    },
    {
      id: 'sarvangasana', name: 'Shoulder Stand', sanskrit: 'Sarvangasana',
      type: 'asana', fig: 'sarvangasana', level: 3, hold: 45,
      breath: 'Slow and even. Never hold the breath here.',
      steps: [
        'Lie on the back with a folded blanket under the shoulders, head on the floor.',
        'Lift the legs and hips, supporting the back with both hands.',
        'Bring the body towards vertical, weight on the shoulders and not the neck.',
        'Come down slowly, one vertebra at a time, and rest.'
      ],
      benefits: ['Traditionally the primary posture for the thyroid', 'Reverses the day-long pull of gravity'],
      helps: ['thyroid', 'sleep', 'immunity'],
      avoid: ['pregnancy', 'hypertension', 'neck', 'glaucoma', 'menstruation', 'heart',
              'disc', 'vertigo', 'osteoporosis', 'recent_surgery'],
      cautions: [
        'Full weight near the neck. Learn this with a teacher present rather than from a screen.',
        'The blanket under the shoulders is not optional - it keeps the neck from taking the load.'
      ],
      mistakes: ['Turning the head while inverted', 'Skipping the shoulder support'],
      mods: ['Legs Up the Wall gives most of the benefit with none of the risk.'],
      alt: 'viparita_karani'
    },
    {
      id: 'halasana', name: 'Plough Pose', sanskrit: 'Halasana',
      type: 'asana', fig: 'halasana', level: 3, hold: 30,
      breath: 'Even breathing; come out if the breath becomes restricted.',
      steps: [
        'From Shoulder Stand, lower the feet over the head towards the floor.',
        'Keep supporting the back with the hands, or interlace the fingers on the floor.',
        'Only let the toes touch down if it costs nothing in the neck.',
        'Roll down slowly with control.'
      ],
      benefits: ['Strong stretch through the whole back', 'Stimulates the thyroid and the abdominal organs'],
      helps: ['thyroid', 'digestion', 'back'],
      avoid: ['pregnancy', 'hypertension', 'neck', 'glaucoma', 'menstruation', 'heart',
              'disc', 'vertigo', 'osteoporosis', 'recent_surgery', 'ulcer'],
      cautions: ['More load on the neck than Shoulder Stand. Not a pose to attempt unsupervised.'],
      mistakes: ['Straining to reach the floor with the toes'],
      mods: ['Rest the feet on a chair placed behind the head.'],
      alt: 'viparita_karani'
    },
    {
      id: 'supta_matsyendrasana', name: 'Reclined Twist', sanskrit: 'Supta Matsyendrasana',
      type: 'asana', fig: 'supta_matsyendrasana', level: 1, hold: 45, sides: true,
      breath: 'Exhale as the knees lower; then breathe slowly and let gravity work.',
      steps: [
        'Lie on the back and draw both knees in.',
        'Exhale and lower both knees to the right, arms out in a T.',
        'Turn the head to the left if the neck is comfortable.',
        'Breathe here, then change sides.'
      ],
      benefits: ['Releases the lower back with no effort required', 'Gently massages the digestive organs'],
      helps: ['back', 'digestion', 'sleep', 'desk'],
      avoid: ['disc', 'recent_surgery', 'pregnancy'],
      cautions: ['In pregnancy use an open twist - let the top knee rest forward on a cushion rather than crossing the body.'],
      mistakes: ['Forcing the top shoulder to the floor'],
      mods: ['Support the lowered knees on a cushion so nothing is straining.'],
      alt: 'pelvic_tilts'
    },
    {
      id: 'uttanpadasana', name: 'Raised Leg Pose', sanskrit: 'Uttanpadasana',
      type: 'asana', fig: 'uttanpadasana', level: 2, hold: 20,
      breath: 'Inhale to lift; breathe steadily and do not hold the breath.',
      steps: [
        'Lie on the back with the legs together, palms down beside the hips.',
        'Inhale and raise both legs to about 45 degrees.',
        'Keep the lower back pressing towards the floor.',
        'Lower slowly on an exhale.'
      ],
      benefits: ['Strengthens the abdominal wall', 'Tones the digestive organs'],
      helps: ['digestion', 'weight', 'back', 'diabetes'],
      avoid: ['pregnancy', 'disc', 'hernia', 'recent_surgery', 'postnatal'],
      cautions: [
        'If the lower back lifts off the floor the abdominals are not holding it - lower the legs or bend the knees.',
        'Not in the first months after birth, and not with any abdominal separation, until cleared.'
      ],
      mistakes: ['Letting the lower back arch away from the floor'],
      mods: ['One leg at a time, or knees bent.'],
      alt: 'pelvic_tilts'
    },
    {
      id: 'ananda_balasana', name: 'Happy Baby', sanskrit: 'Ananda Balasana',
      type: 'asana', fig: 'ananda_balasana', level: 1, hold: 45,
      breath: 'Slow breathing; rock gently side to side if it feels good.',
      steps: [
        'Lie on the back and draw both knees towards the armpits.',
        'Catch the outside edges of the feet, or the backs of the thighs.',
        'Keep the tailbone and the back of the head on the floor.',
        'Rock gently from side to side.'
      ],
      benefits: ['Opens the hips and inner groin', 'Massages the lower back against the floor'],
      helps: ['back', 'digestion', 'postnatal', 'sleep', 'pcos'],
      avoid: ['pregnancy', 'knee', 'neck'],
      cautions: ['Keep the head down throughout - lifting it strains the neck.'],
      mistakes: ['Pulling so hard the tailbone lifts off the floor'],
      mods: ['Hold the backs of the thighs; use a strap around each foot.'],
      alt: 'baddha_konasana'
    },
    {
      id: 'pelvic_tilts', name: 'Pelvic Tilts', sanskrit: '',
      type: 'asana', fig: 'pelvic_tilt', level: 1, hold: 60,
      breath: 'Exhale as the lower back flattens, inhale as it releases.',
      steps: [
        'Lie on the back, knees bent, feet flat and hip-width apart.',
        'Exhale and tilt the pelvis so the lower back presses into the floor.',
        'Inhale and release back to a natural curve.',
        'Repeat slowly, letting the movement stay small.'
      ],
      benefits: ['Wakes up the deep core and pelvic floor without any strain',
                 'The safest lower-back release there is - suitable in pregnancy and just after birth'],
      helps: ['back', 'pregnancy', 'postnatal', 'seniors', 'joints'],
      avoid: [], cautions: [],
      mistakes: ['Making the movement big - it should be barely visible'],
      mods: ['Do the same tilt on all fours or standing against a wall if lying on the back is uncomfortable.'],
      alt: null
    },

    /* ============================== CHAIR ============================= */

    {
      id: 'chair_sit', name: 'Seated Mountain (Chair)', sanskrit: '',
      type: 'asana', fig: 'chair_sit', level: 1, hold: 45,
      breath: 'Slow, even nasal breathing.',
      steps: [
        'Sit forward on the chair so the back is not resting against it.',
        'Both feet flat on the floor, knees over the ankles.',
        'Lengthen up through the spine, shoulders relaxed down.',
        'Rest the hands on the thighs and breathe.'
      ],
      benefits: ['The base for every chair-based practice', 'Rebuilds sitting posture'],
      helps: ['seniors', 'desk', 'joints', 'pregnancy'],
      avoid: [], cautions: [],
      mistakes: ['Slumping back into the chair'],
      mods: ['A cushion behind the lower back if sitting unsupported is tiring.'],
      alt: null
    },
    {
      id: 'chair_twist', name: 'Seated Chair Twist', sanskrit: '',
      type: 'asana', fig: 'chair_twist', level: 1, hold: 30, sides: true,
      breath: 'Inhale to lengthen, exhale to turn.',
      steps: [
        'Sit tall, feet flat on the floor.',
        'Inhale and lengthen the spine.',
        'Exhale and turn to the right, holding the chair back with the right hand.',
        'Unwind on an inhale and change sides.'
      ],
      benefits: ['Restores spinal rotation without getting on the floor', 'Helps a sluggish gut'],
      helps: ['desk', 'digestion', 'back', 'seniors'],
      avoid: ['disc', 'recent_surgery'],
      cautions: ['In pregnancy, turn only from the upper back and keep the belly facing forward.'],
      mistakes: ['Pulling on the chair to force the twist further'],
      mods: ['Hands on the opposite shoulders; turn only halfway.'],
      alt: 'chair_sit'
    },
    {
      id: 'chair_fold', name: 'Seated Forward Fold (Chair)', sanskrit: '',
      type: 'asana', fig: 'chair_fold', level: 1, hold: 40,
      breath: 'Exhale to fold, breathe quietly while hanging.',
      steps: [
        'Sit with the feet flat and the knees apart.',
        'Exhale and fold forward between the knees.',
        'Let the arms and head hang heavy.',
        'Roll up slowly, head last.'
      ],
      benefits: ['The forward-fold release without any load on the legs or balance',
                 'Calming, and possible for almost anyone'],
      helps: ['seniors', 'back', 'desk', 'sleep'],
      avoid: ['glaucoma', 'hypertension'],
      cautions: ['Still a head-below-heart position, if a mild one - come up slowly.'],
      mistakes: ['Coming up quickly'],
      mods: ['Fold only halfway, forearms resting on the thighs.'],
      alt: 'chair_sit'
    },
    {
      id: 'legs_on_chair', name: 'Legs on a Chair', sanskrit: '',
      type: 'relaxation', fig: 'legs_on_chair', level: 1, hold: 180,
      breath: 'Let the breath do whatever it likes.',
      steps: [
        'Lie on the floor with a chair in front of you.',
        'Rest both calves on the chair seat, knees bent at a right angle.',
        'Let the lower back settle completely into the floor.',
        'Stay for several minutes.'
      ],
      benefits: ['Almost all the benefit of Legs Up the Wall with none of the hamstring demand',
                 'Takes every bit of load off the lower back'],
      helps: ['sleep', 'back', 'seniors', 'hypertension', 'pregnancy', 'pcos', 'joints'],
      avoid: [],
      cautions: ['In pregnancy, prop the upper body on cushions so you are reclined rather than flat.'],
      mistakes: ['Using a chair so high the hips lift off the floor'],
      mods: ['A cushion under the head; a folded blanket over the belly.'],
      alt: null
    },

    /* ===================== MOBILITY AND WARM-UPS ====================== */

    {
      id: 'neck_rolls', name: 'Neck Release', sanskrit: 'Griva Sanchalana',
      type: 'mobility', fig: 'neck_tilt', level: 1, hold: 60,
      breath: 'Exhale as the head lowers, inhale as it returns.',
      steps: [
        'Sit tall. Exhale and drop the right ear towards the right shoulder.',
        'Hold for three breaths, then return to centre.',
        'Repeat to the left, then forward with the chin to the chest.',
        'Move slowly and never force the range.'
      ],
      benefits: ['Releases the neck and upper trapezius', 'Often relieves tension headaches at the source'],
      helps: ['desk', 'migraine', 'eyes', 'seniors'],
      avoid: ['neck', 'vertigo'],
      cautions: [
        'Do not roll the head all the way back - full circles compress the small joints of the neck.',
        'With vertigo, keep the eyes open and the movements very small.'
      ],
      mistakes: ['Making full head circles', 'Lifting the shoulder to meet the ear'],
      mods: ['Let the opposite hand hold the chair seat to keep that shoulder down.'],
      alt: 'chair_sit'
    },
    {
      id: 'shoulder_rolls', name: 'Shoulder Rotations', sanskrit: 'Skandha Chakra',
      type: 'mobility', fig: 'shoulder_roll', level: 1, hold: 60,
      breath: 'Inhale as the elbows rise, exhale as they lower.',
      steps: [
        'Sit tall and place the fingertips on the shoulders.',
        'Draw big slow circles with the elbows, forwards five times.',
        'Then backwards five times.',
        'Let the shoulder blades move as much as the shoulders do.'
      ],
      benefits: ['Frees the shoulder girdle so the rib cage can expand', 'A 60-second fix for desk stiffness'],
      helps: ['desk', 'respiratory', 'joints', 'seniors', 'pregnancy'],
      avoid: [],
      cautions: ['With a shoulder injury, keep the circles small and pain-free.'],
      mistakes: ['Rushing - the value is in the slow, full circle'],
      mods: ['Arms by the sides, simply shrugging and rolling the shoulders.'],
      alt: null
    },
    {
      id: 'joint_rotations', name: 'Wrist and Ankle Rotations', sanskrit: 'Pawanmuktasana (part 1)',
      type: 'mobility', fig: 'joint_rotation', level: 1, hold: 90,
      breath: 'Natural breathing throughout.',
      steps: [
        'Sit with the legs extended, hands resting on the thighs.',
        'Rotate both ankles ten times one way, ten times the other.',
        'Flex and point the feet ten times.',
        'Repeat with the wrists: rotate, then open and close the fists.'
      ],
      benefits: ['Warms and lubricates the small joints before anything harder',
                 'The safest possible starting practice at any age'],
      helps: ['joints', 'seniors', 'pregnancy', 'desk', 'diabetes'],
      avoid: [], cautions: [],
      mistakes: ['Going too fast to feel the full range'],
      mods: ['Do it seated on a chair.'],
      alt: null
    },

    /* =========================== PRANAYAMA ============================ */

    {
      id: 'deep_breathing', name: 'Deep Yogic Breath', sanskrit: 'Dirgha Pranayama',
      type: 'pranayama', fig: 'belly_breath', level: 1, hold: 120,
      pace: { in: 4, hold1: 0, out: 6, hold2: 0 },
      breath: 'Inhale for 4, exhale for 6. The longer exhale is what calms the system.',
      steps: [
        'Sit comfortably, one hand on the belly and one on the chest.',
        'Inhale slowly so the belly rises first, then the ribs, then the chest.',
        'Exhale in reverse - chest, ribs, belly - and make the exhale longer than the inhale.',
        'Continue for two to five minutes.'
      ],
      benefits: ['Restores full use of the diaphragm, which most adults have half lost',
                 'A longer exhale directly slows the heart rate'],
      helps: ['respiratory', 'sleep', 'hypertension', 'pregnancy', 'migraine', 'immunity', 'desk'],
      avoid: [], cautions: [],
      mistakes: ['Lifting the shoulders to inhale', 'Making the breath so big it becomes strained'],
      mods: ['Lie down with a book on the belly to see the movement.'],
      alt: null
    },
    {
      id: 'anulom_vilom', name: 'Alternate Nostril Breathing', sanskrit: 'Anulom Vilom',
      type: 'pranayama', fig: 'anulom_vilom', level: 1, hold: 180,
      pace: { in: 4, hold1: 0, out: 6, hold2: 0, alternate: true },
      breath: 'Inhale left, exhale right; inhale right, exhale left. That is one round.',
      steps: [
        'Sit tall. Fold the index and middle fingers of the right hand into the palm.',
        'Close the right nostril with the thumb and inhale through the left.',
        'Close the left with the ring finger, release the thumb, exhale right.',
        'Inhale right, close it, exhale left. Continue for several minutes.'
      ],
      benefits: ['The most broadly useful breathing practice there is - calming without being sedating',
                 'Clears the nasal passages and evens out the breath'],
      helps: ['respiratory', 'hypertension', 'sleep', 'migraine', 'pcos', 'thyroid', 'immunity', 'pregnancy'],
      avoid: [],
      cautions: ['In pregnancy, do it without any breath retention.',
                 'If one nostril is fully blocked, skip it today rather than forcing.'],
      mistakes: ['Pressing the nostril hard enough to distort the nose', 'Rushing the exhale'],
      mods: ['Skip the hand entirely and simply alternate your attention between the nostrils.'],
      alt: 'deep_breathing'
    },
    {
      id: 'bhramari', name: 'Humming Bee Breath', sanskrit: 'Bhramari',
      type: 'pranayama', fig: 'bhramari', level: 1, hold: 150,
      pace: { in: 4, hold1: 0, out: 8, hold2: 0 },
      breath: 'Inhale through the nose; hum steadily all the way through the exhale.',
      steps: [
        'Sit comfortably and close the eyes.',
        'Close the ears with the thumbs or press the small flap of each ear shut.',
        'Inhale through the nose, then hum a low steady "mmm" for the whole exhale.',
        'Repeat five to ten rounds, then sit still and notice the quiet.'
      ],
      benefits: ['The vibration is unusually effective at settling an agitated mind',
                 'Reliably lowers blood pressure and eases tension headaches'],
      helps: ['sleep', 'migraine', 'hypertension', 'pregnancy', 'thyroid', 'desk'],
      avoid: [],
      cautions: ['Skip if you have an active ear infection.'],
      mistakes: ['Humming loudly - it should be low and soft'],
      mods: ['Leave the ears open if closing them is uncomfortable.'],
      alt: 'deep_breathing'
    },
    {
      id: 'ujjayi', name: 'Ocean Breath', sanskrit: 'Ujjayi',
      type: 'pranayama', fig: 'sukhasana', level: 2, hold: 150,
      pace: { in: 5, hold1: 0, out: 5, hold2: 0 },
      breath: 'Slightly narrow the throat so the breath makes a soft ocean sound.',
      steps: [
        'Sit tall and breathe through the nose.',
        'Gently constrict the back of the throat, as if fogging a mirror with the mouth closed.',
        'Keep the sound soft and even on both the inhale and the exhale.',
        'Continue for two to three minutes.'
      ],
      benefits: ['Slows the breath naturally by adding resistance', 'Gives the mind an audible anchor'],
      helps: ['respiratory', 'sleep', 'hypertension', 'desk'],
      avoid: [],
      cautions: ['In pregnancy keep it gentle and never hold the breath.'],
      mistakes: ['Forcing a loud rasping sound - that irritates the throat'],
      mods: ['Practise the sound on the exhale only at first.'],
      alt: 'deep_breathing'
    },
    {
      id: 'kapalabhati', name: 'Skull-Shining Breath', sanskrit: 'Kapalabhati',
      type: 'kriya', fig: 'belly_breath', level: 3, hold: 60,
      breath: 'Short sharp exhales through the nose; the inhale happens by itself.',
      steps: [
        'Sit tall with one hand on the lower belly.',
        'Exhale sharply through the nose by snapping the belly in.',
        'Let the inhale happen passively as the belly releases.',
        'Do 20 to 30 strokes, then breathe normally. Up to three rounds.'
      ],
      benefits: ['Strongly stimulates digestion and the abdominal organs',
                 'Clears the nasal passages and sharpens alertness'],
      helps: ['digestion', 'weight', 'respiratory', 'diabetes', 'pcos'],
      avoid: ['pregnancy', 'hypertension', 'heart', 'hernia', 'ulcer', 'epilepsy',
              'menstruation', 'recent_surgery', 'glaucoma', 'vertigo', 'postnatal'],
      cautions: [
        'This is the practice with the longest contraindication list in the whole app - the forceful abdominal pumping and the pressure changes rule it out for a lot of people.',
        'Absolutely not during pregnancy.',
        'Stop at once if you feel dizzy.'
      ],
      mistakes: ['Pushing on the inhale instead of letting it happen', 'Doing too many rounds too soon'],
      mods: ['Start with 10 slow strokes and build up over weeks.'],
      alt: 'deep_breathing'
    },
    {
      id: 'bhastrika', name: 'Bellows Breath', sanskrit: 'Bhastrika',
      type: 'pranayama', fig: 'belly_breath', level: 3, hold: 45,
      breath: 'Forceful inhale and forceful exhale, both through the nose, in equal measure.',
      steps: [
        'Sit tall and take a few normal breaths first.',
        'Breathe in and out forcefully through the nose, belly expanding and contracting.',
        'Do 10 to 15 breaths, then inhale deeply and pause before resuming normal breathing.',
        'Up to three rounds with a rest between.'
      ],
      benefits: ['Warming and energising', 'Increases lung capacity and clears the airways'],
      helps: ['respiratory', 'immunity', 'weight'],
      avoid: ['pregnancy', 'hypertension', 'heart', 'hernia', 'ulcer', 'epilepsy',
              'menstruation', 'recent_surgery', 'glaucoma', 'vertigo', 'postnatal'],
      cautions: ['Even more vigorous than Kapalabhati. Stop immediately at any dizziness.'],
      mistakes: ['Going fast before the breath is deep - depth first, then speed'],
      mods: ['Half speed, half the rounds.'],
      alt: 'deep_breathing'
    },
    {
      id: 'sheetali', name: 'Cooling Breath', sanskrit: 'Sheetali',
      type: 'pranayama', fig: 'cooling_breath', level: 1, hold: 90,
      pace: { in: 4, hold1: 0, out: 6, hold2: 0 },
      breath: 'Inhale through the curled tongue, exhale through the nose.',
      steps: [
        'Sit comfortably. Roll the tongue into a tube and let it protrude slightly.',
        'Inhale slowly through the rolled tongue - the air feels cool.',
        'Draw the tongue in, close the mouth, and exhale through the nose.',
        'Repeat 8 to 10 rounds.'
      ],
      benefits: ['Genuinely cools the body', 'Helps with acidity, irritability and heat'],
      helps: ['hypertension', 'migraine', 'digestion'],
      avoid: ['asthma'],
      cautions: ['Drawing cold air straight in can trigger asthma or a cough - not one for a reactive airway, and not in cold weather.'],
      mistakes: ['Inhaling so fast the throat dries out'],
      mods: ['If the tongue will not roll, use Sheetkari instead: inhale through lightly clenched teeth.'],
      alt: 'deep_breathing'
    },
    {
      id: 'sheetkari', name: 'Hissing Breath', sanskrit: 'Sheetkari',
      type: 'pranayama', fig: 'cooling_breath', level: 1, hold: 90,
      pace: { in: 4, hold1: 0, out: 6, hold2: 0 },
      breath: 'Inhale through the teeth with a hiss, exhale through the nose.',
      steps: [
        'Bring the teeth lightly together and part the lips.',
        'Inhale slowly through the teeth, making a soft hissing sound.',
        'Close the mouth and exhale through the nose.',
        'Repeat 8 to 10 rounds.'
      ],
      benefits: ['The cooling effect of Sheetali for people who cannot roll the tongue'],
      helps: ['hypertension', 'migraine', 'digestion'],
      avoid: ['asthma'],
      cautions: ['Skip with sensitive teeth, and with any reactive airway.'],
      mistakes: ['Clenching the jaw'],
      mods: ['Part the teeth very slightly to soften the airflow.'],
      alt: 'deep_breathing'
    },
    {
      id: 'chandra_bhedana', name: 'Left Nostril Breathing', sanskrit: 'Chandra Bhedana',
      type: 'pranayama', fig: 'anulom_vilom', level: 2, hold: 120,
      pace: { in: 4, hold1: 0, out: 6, hold2: 0 },
      breath: 'Inhale left only, exhale right only.',
      steps: [
        'Close the right nostril with the right thumb.',
        'Inhale slowly through the left nostril.',
        'Close the left, release the right, and exhale through the right.',
        'Repeat for 10 to 15 rounds.'
      ],
      benefits: ['Cooling and quietening', 'Useful in the evening or when the mind will not settle'],
      helps: ['sleep', 'hypertension', 'migraine'],
      avoid: [],
      cautions: ['Not the one to choose when you already feel sluggish or low.'],
      mistakes: ['Doing it just before something that needs alertness'],
      mods: ['Alternate Nostril Breathing is a gentler general-purpose substitute.'],
      alt: 'anulom_vilom'
    },
    {
      id: 'surya_bhedana', name: 'Right Nostril Breathing', sanskrit: 'Surya Bhedana',
      type: 'pranayama', fig: 'anulom_vilom', level: 2, hold: 120,
      pace: { in: 4, hold1: 0, out: 6, hold2: 0 },
      breath: 'Inhale right only, exhale left only.',
      steps: [
        'Close the left nostril with the right ring finger.',
        'Inhale slowly through the right nostril.',
        'Close the right, release the left, exhale through the left.',
        'Repeat for 10 to 15 rounds.'
      ],
      benefits: ['Warming and activating', 'Traditionally used to stoke a slow digestive fire'],
      helps: ['digestion', 'weight'],
      avoid: ['hypertension', 'pregnancy', 'epilepsy', 'heart'],
      cautions: ['Warming and stimulating - the wrong choice with high blood pressure or in the evening.'],
      mistakes: ['Practising it late at night and then being unable to sleep'],
      mods: ['Alternate Nostril Breathing if in any doubt.'],
      alt: 'anulom_vilom'
    },
    {
      id: 'box_breathing', name: 'Box Breathing', sanskrit: 'Sama Vritti',
      type: 'pranayama', fig: 'sukhasana', level: 1, hold: 160,
      pace: { in: 4, hold1: 4, out: 4, hold2: 4 },
      breath: 'Inhale 4, hold 4, exhale 4, hold 4.',
      steps: [
        'Sit comfortably with the spine tall.',
        'Inhale through the nose for a count of four.',
        'Hold for four, exhale for four, hold empty for four.',
        'Continue for two to four minutes.'
      ],
      benefits: ['Steadies the mind quickly', 'Easy to do anywhere, including at a desk'],
      helps: ['sleep', 'hypertension', 'migraine', 'desk'],
      avoid: ['pregnancy'],
      cautions: ['Breath retention is generally left out during pregnancy - use the Deep Yogic Breath instead.'],
      mistakes: ['Choosing a count so long the breath becomes a struggle'],
      mods: ['Drop to a count of three, or remove the holds entirely.'],
      alt: 'deep_breathing'
    },
    {
      id: 'four_seven_eight', name: '4-7-8 Breath', sanskrit: '',
      type: 'pranayama', fig: 'sukhasana', level: 2, hold: 120,
      pace: { in: 4, hold1: 7, out: 8, hold2: 0 },
      breath: 'Inhale 4, hold 7, exhale 8 through the mouth.',
      steps: [
        'Sit or lie down comfortably.',
        'Inhale quietly through the nose for four counts.',
        'Hold the breath for seven counts.',
        'Exhale through the mouth for eight counts. Four rounds is enough.'
      ],
      benefits: ['A very long exhale, which is the most direct way to trigger the relaxation response',
                 'Widely used as a way into sleep'],
      helps: ['sleep', 'migraine'],
      avoid: ['pregnancy', 'hypertension'],
      cautions: ['The seven-count hold makes this unsuitable in pregnancy and with uncontrolled blood pressure.'],
      mistakes: ['Doing more than four rounds when new to it - it can leave you light-headed'],
      mods: ['Halve every count and keep the ratio.'],
      alt: 'deep_breathing'
    },
    {
      id: 'agnisar', name: 'Agnisar Kriya', sanskrit: 'Agnisar Kriya',
      type: 'kriya', fig: 'belly_breath', level: 3, hold: 60,
      breath: 'Done on an empty exhale - the breath stays out while the belly moves.',
      steps: [
        'Stand with the feet apart, knees slightly bent, hands on the thighs.',
        'Exhale fully and hold the breath out.',
        'Pump the abdomen in and out rapidly while the breath stays out.',
        'Inhale when you need to. Rest, then repeat up to three times.'
      ],
      benefits: ['One of the strongest traditional practices for a sluggish digestive system'],
      helps: ['digestion', 'weight', 'diabetes'],
      avoid: ['pregnancy', 'ulcer', 'hernia', 'hypertension', 'heart', 'menstruation',
              'recent_surgery', 'postnatal', 'glaucoma'],
      cautions: [
        'Must be done on an empty stomach, first thing in the morning.',
        'The breath retention combined with abdominal pressure rules this out for most medical conditions - when in doubt, do Wind-Relieving Pose instead.'
      ],
      mistakes: ['Practising after a meal'],
      mods: ['Do the belly movement while breathing normally - much milder, still useful.'],
      alt: 'vajrasana'
    },

    /* ==================== RELAXATION AND THE EYES ===================== */

    {
      id: 'yoga_nidra', name: 'Yoga Nidra', sanskrit: 'Yoga Nidra',
      type: 'relaxation', fig: 'yoga_nidra', level: 1, hold: 300,
      breath: 'Natural breathing. The practice is attention, not breath control.',
      steps: [
        'Lie down warm and well supported, a bolster under the knees.',
        'Move the attention slowly through the body, part by part: right hand, right arm, right shoulder, and so on.',
        'Do not try to relax anything - just notice each part in turn.',
        'Finish by feeling the whole body at once, then the breath.'
      ],
      benefits: ['Deep rest that is measurably more restorative than the same time asleep for many people',
                 'Works when the mind is too busy for seated meditation'],
      helps: ['sleep', 'migraine', 'hypertension', 'postnatal', 'immunity', 'pregnancy'],
      avoid: [],
      cautions: ['In pregnancy, do it lying on the left side rather than on the back.'],
      mistakes: ['Trying to stay awake - falling asleep is fine, especially at first'],
      mods: ['Do it seated in a reclined chair.'],
      alt: 'savasana'
    },
    {
      id: 'side_lying_rest', name: 'Side-Lying Relaxation', sanskrit: '',
      type: 'relaxation', fig: 'side_lying', level: 1, hold: 240,
      breath: 'Let the breath settle by itself.',
      steps: [
        'Lie on the left side with a pillow under the head.',
        'Bend the top knee and rest it on a cushion or bolster.',
        'Let the bottom arm rest forward and the top hand rest wherever it is comfortable.',
        'Stay for several minutes.'
      ],
      benefits: ['The safe deep-rest position in later pregnancy',
                 'The left side keeps the weight off the vein that returns blood from the legs'],
      helps: ['pregnancy', 'postnatal', 'sleep', 'back'],
      avoid: [], cautions: [],
      mistakes: ['Lying without a cushion between the knees, which twists the pelvis'],
      mods: ['A rolled blanket under the belly is a comfort worth trying in the third trimester.'],
      alt: null
    },
    {
      id: 'eye_exercises', name: 'Eye Exercises', sanskrit: 'Netra Vyayama',
      type: 'mobility', fig: 'eye_exercise', level: 1, hold: 120,
      breath: 'Breathe normally; blink freely between each movement.',
      steps: [
        'Sit tall and keep the head completely still.',
        'Look up, then down. Repeat ten times, then close the eyes and rest.',
        'Look right, then left, ten times. Rest.',
        'Finish by rubbing the palms warm and cupping them over closed eyes for a minute.'
      ],
      benefits: ['Moves eye muscles locked into one focal distance all day',
                 'The palming at the end is the part that most relieves eye strain'],
      helps: ['eyes', 'desk', 'migraine'],
      avoid: ['recent_surgery'],
      cautions: ['Skip after any recent eye surgery or injury until cleared.'],
      mistakes: ['Moving the head instead of only the eyes', 'Skipping the palming'],
      mods: ['Do half the repetitions and twice the palming.'],
      alt: null
    },
    {
      id: 'trataka', name: 'Candle Gazing', sanskrit: 'Trataka',
      type: 'relaxation', fig: 'trataka', level: 2, hold: 180,
      breath: 'Slow and quiet.',
      steps: [
        'Place a candle at arm\'s length, flame at eye level, in a dim draught-free room.',
        'Gaze softly at the flame without blinking for as long as is comfortable.',
        'When the eyes water, close them and hold the after-image behind the eyelids.',
        'Repeat two or three times, then rest with the eyes closed.'
      ],
      benefits: ['Trains steady attention', 'Traditionally used to strengthen the eyes and quieten the mind'],
      helps: ['eyes', 'sleep'],
      avoid: ['glaucoma', 'epilepsy'],
      cautions: [
        'A flickering flame can trigger a seizure in photosensitive epilepsy.',
        'Skip with glaucoma or any eye condition without checking first.'
      ],
      mistakes: ['Straining not to blink - watering eyes are the signal to close them'],
      mods: ['Gaze at a small dot on the wall instead of a flame.'],
      alt: 'eye_exercises'
    }
  ];

  /** id -> pose, built once. */
  YG.POSE_BY_ID = {};
  for (var i = 0; i < YG.POSES.length; i++) {
    YG.POSE_BY_ID[YG.POSES[i].id] = YG.POSES[i];
  }
})(window.YG = window.YG || {});
