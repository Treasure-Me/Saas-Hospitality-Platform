// Menu Data Structure (Ready to be replaced by a Fetch API call)
const menuData = [
    { name: "Famous Zen Wings", category: "wings", price: "R120", desc: "Signature grilled or crispy fried wings with chips." },
    { name: "Mogodo & Dumplings", category: "heritage", price: "R110", desc: "Slow cooked tripe served with traditional dumplings." },
    { name: "Beef Stew & Spinach", category: "heritage", price: "R115", desc: "Tender beef stew with creamed savory spinach." },
    { name: "Lounge Snack Platter", category: "all", price: "R95", desc: "Premium biltong, assorted cheeses, and cashews." }
];

function renderMenu(filter = 'all') {
    const container = document.getElementById('menu-container');
    container.innerHTML = '';
    
    const filtered = filter === 'all' ? menuData : menuData.filter(item => item.category === filter);
    
    filtered.forEach(item => {
        container.innerHTML += `
            <div class="border-b border-white/5 pb-4 flex justify-between items-end">
                <div>
                    <h3 class="text-lg font-bold">${item.name}</h3>
                    <p class="text-xs text-gray-500">${item.desc}</p>
                </div>
                <span class="text-amber-500 font-bold serif">${item.price}</span>
            </div>
        `;
    });
}

function filterMenu(cat) {
    document.querySelectorAll('.menu-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');
    renderMenu(cat);
}

// Initial Render
renderMenu();

// Scroll Animations
window.addEventListener('scroll', () => {
    // Nav Background change
    const nav = document.getElementById('main-nav');
    if (window.scrollY > 50) {
        nav.style.background = 'rgba(10, 10, 10, 0.95)';
    } else {
        nav.style.background = 'transparent';
    }

    // Reveal elements
    document.querySelectorAll('.reveal').forEach(el => {
        if (el.getBoundingClientRect().top < window.innerHeight - 100) {
            el.classList.add('active');
        }
    });
});

// Booking Integration Point
document.getElementById('bookingForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button');
    btn.innerText = "SENDING...";
    
    // BACKEND HOOK: Replace this timeout with your fetch('/api/book') call
    setTimeout(() => {
        alert("Booking request received! Zen 63 will contact you via +27 66 519 2126 to confirm.");
        btn.innerText = "REQUEST BOOKING";
        e.target.reset();
    }, 2000);
});